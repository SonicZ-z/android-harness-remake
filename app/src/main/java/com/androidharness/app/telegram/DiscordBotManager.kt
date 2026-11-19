package com.androidharness.app.telegram

import android.content.Context
import com.androidharness.app.AppContainer
import com.androidharness.app.tools.SecretRedactor
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Serializable
internal data class DiscordBotConfig(
    val token: String = "",
    val botId: String? = null,
    val botName: String? = null,
    val ownerId: String? = null,
    val enabled: Boolean = false,
    val projectId: String? = null,
    val sessionId: String? = null,
    val customCommands: List<String> = emptyList(),
) {
    override fun toString() = "DiscordBotConfig(botId=$botId, ownerId=$ownerId, enabled=$enabled, token=[redacted])"
}

internal data class DiscordBotSetupState(
    val botName: String? = null,
    val botId: String? = null,
    val ownerId: String? = null,
    val enabled: Boolean = false,
    val customCommands: List<String> = emptyList(),
)

internal data class DiscordBotRuntimeState(
    val running: Boolean = false,
    val connected: Boolean = false,
    val message: String? = null,
)

/** Discord's token is stored with the app's Android Keystore-backed secret store. */
internal class DiscordBotManager(private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageLock = Mutex()
    private val dispatchJobsLock = Any()
    private val dispatchJobs = mutableSetOf<Job>()
    private val configurationGeneration = AtomicLong(0)
    private val bridge = AndroidTelegramAgentBridge(c)
    private val http = OkHttpClient.Builder()
        .callTimeout(java.time.Duration.ofSeconds(20))
        .build()
    @Volatile private var config = runCatching {
        c.keys.localSecret(STORE_SLOT)?.let { json.decodeFromString<DiscordBotConfig>(it) }
    }.getOrNull() ?: DiscordBotConfig()
    private val setupMutable = MutableStateFlow(config.toSetupState())
    val setup = setupMutable.asStateFlow()
    private val runtimeMutable = MutableStateFlow(DiscordBotRuntimeState())
    val runtime = runtimeMutable.asStateFlow()
    private val errorMutable = MutableStateFlow<String?>(null)
    val error = errorMutable.asStateFlow()
    private var runtimeJob: Job? = null
    private var runtimeOwner: Any? = null
    private val scriptDirectory = File(c.appContext.filesDir, SCRIPT_DIRECTORY)
    private val gateway = DiscordGatewayRunner(this, http)

    private fun DiscordBotConfig.toSetupState() = DiscordBotSetupState(
        botName = botName,
        botId = botId,
        ownerId = ownerId,
        enabled = enabled,
        customCommands = customCommands,
    )

    @Synchronized
    internal fun currentConfig(): DiscordBotConfig = config

    @Synchronized
    private fun save(next: DiscordBotConfig) {
        c.keys.putLocalSecret(STORE_SLOT, json.encodeToString(DiscordBotConfig.serializer(), next))
        val previous = config
        if (previous.token != next.token || previous.botId != next.botId || previous.ownerId != next.ownerId || previous.enabled != next.enabled) {
            configurationGeneration.incrementAndGet()
        }
        config = next
        setupMutable.value = next.toSetupState()
    }

    private suspend fun cancelMessageJobsAndJoin() {
        val pending = synchronized(dispatchJobsLock) { dispatchJobs.toList() }
        pending.forEach { it.cancel(CancellationException("Discord bot configuration changed")) }
        pending.joinAll()
    }

    suspend fun connect(token: String, ownerId: String) = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanOwner = ownerId.trim()
        require(cleanToken.length in 45..120) { "Enter a Discord bot token." }
        require(cleanOwner.matches(Regex("[0-9]{17,20}"))) { "Enter the numeric Discord user ID that is allowed to control this bot." }
        pause()
        val identity = DiscordRestClient(cleanToken, http).getBotIdentity()
        require(identity.id != cleanOwner) { "The allowed owner ID must be a user account, not the bot itself." }
        synchronized(this@DiscordBotManager) {
            val ownerChanged = config.ownerId != cleanOwner || config.botId != identity.id
            save(config.copy(
                token = cleanToken,
                botId = identity.id,
                botName = identity.username,
                ownerId = cleanOwner,
                enabled = false,
                projectId = if (ownerChanged) null else config.projectId,
                sessionId = if (ownerChanged) null else config.sessionId,
            ))
            errorMutable.value = null
        }
    }

    suspend fun disconnect() {
        pause()
        synchronized(this) {
            c.keys.removeLocalSecret(STORE_SLOT)
            config = config.copy(token = "", botId = null, botName = null, ownerId = null, enabled = false,
                projectId = null, sessionId = null)
            setupMutable.value = config.toSetupState()
        }
    }

    suspend fun enable() {
        check(c.runtimePolicy.canRun()) { "Background running is disabled. Enable it in app settings first." }
        val saved = currentConfig()
        check(saved.token.isNotBlank() && saved.botId != null && saved.ownerId != null) { "Connect a Discord bot and set its allowed owner first." }
        synchronized(this) { save(config.copy(enabled = true)) }
        errorMutable.value = null
        DiscordBotService.start(c.appContext)
    }

    suspend fun pause() {
        synchronized(this) { if (config.enabled) save(config.copy(enabled = false)) }
        runtimeJob?.cancel()
        runtimeJob?.join()
        runtimeJob = null
        cancelMessageJobsAndJoin()
        runtimeMutable.value = DiscordBotRuntimeState()
        DiscordBotService.stop(c.appContext)
    }

    fun restoreIfEnabled() {
        scope.launch {
            if (currentConfig().enabled && c.runtimePolicy.canRun()) {
                runCatching { DiscordBotService.start(c.appContext) }.onFailure {
                    errorMutable.value = "Open Android Harness Remake and reconnect the Discord bot."
                }
            }
        }
    }

    suspend fun suspendForBackground() = pause()

    @Synchronized
    fun startRuntime(owner: Any? = null) {
        runtimeOwner = owner
        if (!config.enabled || runtimeJob?.isActive == true) return
        runtimeMutable.value = DiscordBotRuntimeState(running = true, message = "Connecting to Discord…")
        runtimeJob = scope.launch {
            try {
                c.runtimePolicy.whileAllowed { gateway.run() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMutable.value = SecretRedactor.redact(e.message ?: "Discord bot stopped. Check the connection and retry.")
                synchronized(this@DiscordBotManager) {
                    if (config.enabled) save(config.copy(enabled = false))
                }
                cancelMessageJobsAndJoin()
                runtimeMutable.value = DiscordBotRuntimeState(message = errorMutable.value)
                DiscordBotService.stop(c.appContext)
            }
        }
    }

    suspend fun serviceDestroyed(owner: Any? = null) {
        if (runtimeOwner !== owner) return
        runtimeJob?.cancel()
        runtimeJob?.join()
        cancelMessageJobsAndJoin()
        runtimeJob = null
        runtimeOwner = null
    }

    internal fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    internal fun setConnected(connected: Boolean, message: String? = null) {
        runtimeMutable.value = runtimeMutable.value.copy(running = true, connected = connected, message = message)
    }

    internal fun dispatchMessage(event: JSONObject) {
        if (!currentConfig().enabled) return
        val generation = configurationGeneration.get()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                messageLock.withLock {
                    if (generation != configurationGeneration.get()) return@withLock
                    try {
                        handleMessage(event, generation)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorMutable.value = SecretRedactor.redact(e.message ?: "Could not handle the Discord message.")
                    }
                }
            } finally {
                synchronized(dispatchJobsLock) { dispatchJobs.remove(coroutineContext[Job]) }
            }
        }
        val accepted = synchronized(dispatchJobsLock) {
            if (dispatchJobs.size >= MAX_DISPATCH_JOBS) false else dispatchJobs.add(job)
        }
        if (!accepted) {
            job.cancel()
            errorMutable.value = "Discord is busy. The newest message was ignored; wait for the current task to finish and try again."
            return
        }
        job.invokeOnCompletion { synchronized(dispatchJobsLock) { dispatchJobs.remove(job) } }
        job.start()
    }

    private suspend fun handleMessage(event: JSONObject, generation: Long) {
        if (generation != configurationGeneration.get()) return
        val saved = currentConfig().takeIf { it.enabled } ?: return
        val author = event.optJSONObject("author") ?: return
        if (author.optBoolean("bot") || author.optString("id") != saved.ownerId) return
        val botId = saved.botId ?: return
        val raw = event.optString("content").trim()
        if (raw.isBlank()) return
        val guildMessage = !event.isNull("guild_id") && event.has("guild_id")
        val mention = raw.contains("<@$botId>") || raw.contains("<@!$botId>")
        if (guildMessage && !mention) return
        val text = raw.replace("<@!$botId>", "").replace("<@$botId>", "").trim()
        val channelId = event.optString("channel_id").takeIf { it.matches(Regex("[0-9]{17,20}")) }
        val messageId = event.optString("id")
        when {
            text.equals("!help", ignoreCase = true) || text.equals("/help", ignoreCase = true) ->
                deliver("Commands: mention me with a question for an AI reply; use !status, !new, or !run <name> [input]. Custom scripts are created in Android Harness Remake settings.", channelId, guildMessage, generation)
            text.equals("!status", ignoreCase = true) || text.equals("/status", ignoreCase = true) ->
                deliver("Discord bot is ${if (runtime.value.connected) "connected" else "reconnecting"}. AI replies are plan-only. Replies from server channels are sent privately to your Discord account.", channelId, guildMessage, generation)
            text.equals("!new", ignoreCase = true) -> {
                synchronized(this) {
                    if (generation != configurationGeneration.get()) return
                    save(config.copy(sessionId = null))
                }
                deliver("Started a fresh AI conversation. Mention me with a question.", channelId, guildMessage, generation)
            }
            text.startsWith("!run ", ignoreCase = true) -> {
                val commandLine = text.drop(5).trim()
                val name = commandLine.substringBefore(' ').lowercase()
                val input = commandLine.substringAfter(' ', "")
                runCustomCommand(name, input, generation).also { deliver(it, channelId, guildMessage, generation) }
            }
            text.startsWith("!") && text.substringBefore(' ').drop(1).lowercase() in saved.customCommands -> {
                val name = text.substringBefore(' ').drop(1).lowercase()
                val input = text.substringAfter(' ', "")
                runCustomCommand(name, input, generation).also { deliver(it, channelId, guildMessage, generation) }
            }
            text.isBlank() -> deliver("Mention me with a question, or send !help.", channelId, guildMessage, generation)
            else -> deliver(runAi(text, messageId, generation), channelId, guildMessage, generation)
        }
    }

    private suspend fun runAi(prompt: String, discordMessageId: String, generation: Long): String {
        if (generation != configurationGeneration.get()) return "The Discord bot setup changed. Please try again."
        val saved = currentConfig().takeIf { it.enabled } ?: return "The Discord bot is paused."
        val projectId = saved.projectId ?: c.workspace.currentProjectOnce().id
        var sessionId = saved.sessionId
        if (sessionId == null) {
            sessionId = bridge.newChat(projectId, "Discord AI chat")
            if (generation != configurationGeneration.get()) return "The Discord bot setup changed. Please try again."
            synchronized(this) {
                if (generation != configurationGeneration.get()) return "The Discord bot setup changed. Please try again."
                save(config.copy(projectId = projectId, sessionId = sessionId))
            }
        }
        if (generation != configurationGeneration.get()) return "The Discord bot setup changed. Please try again."
        if (bridge.isBusy(sessionId)) return "The linked AI chat is busy or paused. Open the app to finish or stop that task, then try again."
        val target = bridge.target(projectId, sessionId, "PLAN")
        val requestId = discordMessageId.toLongOrNull()?.takeIf { it > 0 } ?: System.currentTimeMillis()
        val request = TelegramRequest(
            id = requestId,
            text = prompt.take(8_000),
            receivedAt = System.currentTimeMillis(),
            status = TelegramRequestStatus.RUNNING,
            sessionId = target.sessionId,
            projectId = target.projectId,
            providerId = target.providerId,
            model = target.model,
            mode = "PLAN",
        )
        val binding = "discord-${saved.botId}-${saved.ownerId}"
        if (generation != configurationGeneration.get()) return "The Discord bot setup changed. Please try again."
        bridge.start(request, binding)
        val deadline = System.currentTimeMillis() + AI_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (generation != configurationGeneration.get()) throw CancellationException("Discord bot configuration changed")
            val snapshot = bridge.snapshot(request, binding)
            if (snapshot.completed) return safeText(snapshot.output.ifBlank { "The AI finished without a text response." })
            if (snapshot.cancelled) return "The AI conversation was stopped in the app."
            if (!snapshot.running && snapshot.started && snapshot.error != null) return safeText(snapshot.error)
            delay(500)
        }
        return "The AI is still working. Check the conversation in Android Harness Remake before sending another prompt."
    }

    private suspend fun runCustomCommand(name: String, input: String, generation: Long): String = withContext(Dispatchers.IO) {
        if (generation != configurationGeneration.get()) return@withContext "The Discord bot setup changed. Please try again."
        val saved = currentConfig().takeIf { it.enabled } ?: return@withContext "The Discord bot is paused."
        require(name.matches(COMMAND_NAME) && name in saved.customCommands) { "That Python command is not registered in the app." }
        check(c.linuxEnv.isReady) { "Install the app's Linux environment and Python before running custom commands." }
        scriptDirectory.mkdirs()
        val base = scriptDirectory.canonicalFile
        val script = File(base, "$name.py").canonicalFile
        check(script.parentFile == base && script.isFile) { "The saved Python command is unavailable." }
        val scriptPath = JSONObject.quote(script.absolutePath)
        val launcher = """
            import os, runpy, sys
            try:
                os.setsid()
            except OSError:
                pass
            group = os.getpgrp()
            os.write(1, (str(group if group == os.getpid() else 0) + "\n").encode())
            path = $scriptPath
            sys.argv = [path]
            runpy.run_path(path, run_name="__main__")
        """.trimIndent()
        val process = c.linuxEnv.startShell("python3 -c ${shellQuote(launcher)} 2>&1", base).first
        val groupId = CompletableDeferred<Int>()
        val output = async(Dispatchers.IO) { readProcessOutput(process.inputStream, MAX_SCRIPT_OUTPUT, groupId) }
        try {
            process.outputStream.use { stream ->
                stream.write(input.take(MAX_SCRIPT_INPUT).toByteArray(StandardCharsets.UTF_8))
            }
            val finished = runInterruptible { process.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            if (!finished) {
                terminateProcessGroup(process, withTimeoutOrNull(500) { groupId.await() } ?: -1)
                return@withContext "The Python command exceeded ${SCRIPT_TIMEOUT_SECONDS}s and was stopped."
            }
            val result = withTimeoutOrNull(1_000) { output.await() }
            if (result == null) {
                terminateProcessGroup(process, withTimeoutOrNull(250) { groupId.await() } ?: -1)
                return@withContext "The Python command left a child process running and was stopped."
            }
            if (process.exitValue() != 0) {
                return@withContext "Python command failed (exit ${process.exitValue()}): ${safeText(result).take(1200)}"
            }
            safeText(result.ifBlank { "Command finished with no output." }).take(1800)
        } finally {
            val processGroup = withContext(NonCancellable) { withTimeoutOrNull(500) { groupId.await() } ?: -1 }
            terminateProcessGroup(process, processGroup)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            withContext(NonCancellable) { output.cancelAndJoin() }
        }
    }

    fun saveCustomCommand(name: String, source: String) {
        val cleanName = name.trim().lowercase()
        require(cleanName.matches(COMMAND_NAME)) { "Use a command name with 1 to 24 lowercase letters, numbers, or underscores. Start with a letter." }
        require(source.isNotBlank() && source.length <= MAX_SCRIPT_SOURCE) { "Python source must be between 1 and $MAX_SCRIPT_SOURCE characters." }
        scriptDirectory.mkdirs()
        val base = scriptDirectory.canonicalFile
        val script = File(base, "$cleanName.py").canonicalFile
        require(script.parentFile == base) { "Invalid script path." }
        val temporary = File(base, ".$cleanName.tmp")
        temporary.writeText(source, StandardCharsets.UTF_8)
        check(temporary.renameTo(script)) { "Could not save the Python command." }
        synchronized(this) { save(config.copy(customCommands = (config.customCommands + cleanName).distinct().sorted())) }
    }

    fun removeCustomCommand(name: String) {
        val cleanName = name.trim().lowercase()
        require(cleanName.matches(COMMAND_NAME))
        val base = scriptDirectory.canonicalFile
        val script = File(base, "$cleanName.py").canonicalFile
        require(script.parentFile == base)
        script.delete()
        synchronized(this) { save(config.copy(customCommands = config.customCommands - cleanName)) }
    }

    private suspend fun deliver(text: String, incomingChannelId: String?, guildMessage: Boolean, generation: Long) = withContext(Dispatchers.IO) {
        val saved = currentConfig().takeIf { it.enabled && generation == configurationGeneration.get() } ?: return@withContext
        val api = DiscordRestClient(saved.token, http)
        val destination = if (guildMessage || incomingChannelId == null) api.directMessageChannel(requireNotNull(saved.ownerId)) else incomingChannelId
        val clean = safeText(text).ifBlank { "(empty response)" }
        val pieces = clean.chunked(DISCORD_MESSAGE_LIMIT)
        for (piece in pieces.take(MAX_DISCORD_PARTS)) {
            if (generation != configurationGeneration.get()) return@withContext
            api.sendMessage(destination, piece)
        }
    }

    private fun safeText(text: String): String {
        val token = currentConfig().token
        return SecretRedactor.redact(if (token.isBlank()) text else text.replace(token, "[redacted]"))
    }

    internal fun gatewayToken(): String? = currentConfig().takeIf { it.enabled }?.token
    internal fun isEnabled(): Boolean = currentConfig().enabled
    internal fun isOwnerMessage(event: JSONObject): Boolean {
        val saved = currentConfig().takeIf { it.enabled } ?: return false
        val author = event.optJSONObject("author") ?: return false
        return !author.optBoolean("bot") && author.optString("id") == saved.ownerId
    }
    internal fun dispatchGatewayMessage(event: JSONObject) = dispatchMessage(event)

    companion object {
        private const val STORE_SLOT = "discord_bot_setup"
        private const val SCRIPT_DIRECTORY = "discord-bot-commands"
        private const val MAX_SCRIPT_SOURCE = 30_000
        private const val MAX_SCRIPT_INPUT = 2_000
        private const val MAX_SCRIPT_OUTPUT = 4_000
        private const val SCRIPT_TIMEOUT_SECONDS = 15L
        private const val AI_TIMEOUT_MS = 120_000L
        private const val MAX_DISPATCH_JOBS = 4
        internal const val DISCORD_MESSAGE_LIMIT = 1_900
        private const val MAX_DISCORD_PARTS = 3
        private val COMMAND_NAME = Regex("[a-z][a-z0-9_]{0,23}")
        private fun shellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

        private fun readProcessOutput(stream: InputStream, maximum: Int, groupId: CompletableDeferred<Int>): String {
            val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
            val parsedGroup = reader.readLine()?.trim()?.toIntOrNull()?.takeIf { it > 1 } ?: -1
            groupId.complete(parsedGroup)
            val output = StringBuilder()
            val buffer = CharArray(1024)
            var remaining = maximum
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (remaining > 0) {
                    val accepted = minOf(count, remaining)
                    output.append(buffer, 0, accepted)
                    remaining -= accepted
                }
            }
            return output.toString()
        }

        private fun terminateProcessGroup(process: Process, groupId: Int) {
            if (groupId > 1) runCatching { android.os.Process.sendSignal(-groupId, 15) }
            if (process.isAlive) runCatching { process.destroy() }
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
            if (groupId > 1) runCatching { android.os.Process.sendSignal(-groupId, 9) }
            if (process.isAlive) runCatching { process.destroyForcibly() }
        }
    }
}

internal data class DiscordBotIdentity(val id: String, val username: String)

internal class DiscordRestClient(private val token: String, private val client: OkHttpClient) {
    private fun request(path: String, method: String = "GET", json: JSONObject? = null): JSONObject {
        val builder = Request.Builder()
            .url("https://discord.com/api/v10$path")
            .header("Authorization", "Bot $token")
            .header("User-Agent", "AndroidHarnessRemake/1.0.0")
        if (method == "POST") {
            val body = (json ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.post(body)
        } else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Discord request failed (HTTP ${response.code}). Check the bot token and Discord permissions.")
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    fun getBotIdentity(): DiscordBotIdentity {
        val me = request("/users/@me")
        val id = me.optString("id")
        val username = me.optString("username")
        if (!id.matches(Regex("[0-9]{17,20}")) || username.isBlank()) throw java.io.IOException("Discord did not return a valid bot identity.")
        return DiscordBotIdentity(id, username)
    }

    fun gatewayUrl(): String {
        val raw = request("/gateway/bot").optString("url")
        if (!raw.startsWith("wss://")) throw java.io.IOException("Discord returned an invalid Gateway URL.")
        return raw + if (raw.contains('?')) "&v=10&encoding=json" else "?v=10&encoding=json"
    }

    fun directMessageChannel(userId: String): String {
        val id = request("/users/@me/channels", "POST", JSONObject().put("recipient_id", userId)).optString("id")
        if (!id.matches(Regex("[0-9]{17,20}"))) throw java.io.IOException("Discord could not open a private chat with the configured owner.")
        return id
    }

    fun sendMessage(channelId: String, text: String) {
        request("/channels/$channelId/messages", "POST", JSONObject()
            .put("content", text.take(DiscordBotManager.DISCORD_MESSAGE_LIMIT))
            .put("allowed_mentions", JSONObject().put("parse", org.json.JSONArray())))
    }
}
