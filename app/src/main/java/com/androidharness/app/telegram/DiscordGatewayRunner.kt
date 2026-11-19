package com.androidharness.app.telegram

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** A deliberately small Discord Gateway client. It accepts only the configured owner. */
internal class DiscordGatewayRunner(
    private val manager: DiscordBotManager,
    private val http: OkHttpClient,
) {
    suspend fun run() {
        val runnerScope = CoroutineScope(currentCoroutineContext())
        var failures = 0
        while (currentCoroutineContext().isActive && manager.isEnabled()) {
            val token = manager.gatewayToken() ?: return
            try {
                val rest = DiscordRestClient(token, http)
                val gatewayUrl = rest.gatewayUrl()
                val ended = CompletableDeferred<CloseInfo>()
                val sequence = AtomicLong(-1)
                val lastHeartbeatSent = AtomicLong(0)
                val lastHeartbeatAck = AtomicLong(0)
                val socketRef = AtomicReference<WebSocket?>()
                val heartbeatRef = AtomicReference<Job?>()

                fun sendHeartbeat(socket: WebSocket): Boolean {
                    val sent = socket.send(heartbeat(sequence.get()))
                    if (sent) {
                        lastHeartbeatSent.set(System.nanoTime())
                    } else {
                        ended.complete(CloseInfo(1006, "Discord heartbeat send failed"))
                        socket.cancel()
                    }
                    return sent
                }

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        socketRef.set(webSocket)
                        manager.setConnected(false, "Discord Gateway opened; waiting for the session handshake.")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val packet = runCatching { JSONObject(text) }.getOrNull() ?: return
                        if (!packet.isNull("s")) sequence.set(packet.optLong("s", -1))
                        when (packet.optInt("op", -1)) {
                            10 -> {
                                val interval = packet.optJSONObject("d")?.optLong("heartbeat_interval", 0L) ?: 0L
                                if (interval <= 0L) {
                                    ended.complete(CloseInfo(4000, "Discord returned an invalid heartbeat interval"))
                                    webSocket.cancel()
                                    return
                                }
                                val identify = JSONObject()
                                    .put("op", 2)
                                    .put("d", JSONObject()
                                        .put("token", token)
                                        .put("intents", INTENT_GUILD_MESSAGES or INTENT_DIRECT_MESSAGES or INTENT_MESSAGE_CONTENT)
                                        .put("properties", JSONObject()
                                            .put("os", "android")
                                            .put("browser", "Android Harness Remake")
                                            .put("device", "Android Harness Remake")))
                                if (!webSocket.send(identify.toString())) {
                                    ended.complete(CloseInfo(1006, "Discord identify send failed"))
                                    webSocket.cancel()
                                    return
                                }
                                heartbeatRef.getAndSet(runnerScope.launch {
                                    while (isActive && !ended.isCompleted) {
                                        delay(interval)
                                        if (ended.isCompleted) break
                                        if (lastHeartbeatSent.get() > lastHeartbeatAck.get()) {
                                            ended.complete(CloseInfo(4000, "Discord heartbeat acknowledgement timed out"))
                                            webSocket.cancel()
                                            break
                                        }
                                        if (!sendHeartbeat(webSocket)) break
                                    }
                                })?.cancel()
                            }
                            0 -> {
                                val type = packet.optString("t")
                                val data = packet.optJSONObject("d") ?: return
                                when (type) {
                                    "READY" -> manager.setConnected(true, "Connected to Discord.")
                                    "MESSAGE_CREATE" -> {
                                        if (!manager.isOwnerMessage(data)) return
                                        val channelId = data.optString("channel_id")
                                        val hasGuild = !data.isNull("guild_id") && data.has("guild_id")
                                        manager.launch {
                                            if (!hasGuild && !isOneToOneDm(token, channelId)) return@launch
                                            manager.dispatchGatewayMessage(data)
                                        }
                                    }
                                }
                            }
                            1 -> sendHeartbeat(webSocket)
                            7, 9 -> {
                                ended.complete(CloseInfo(4000, "Discord requested a reconnect"))
                                webSocket.close(4000, "reconnect")
                            }
                            11 -> lastHeartbeatAck.set(lastHeartbeatSent.get())
                            else -> Unit
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        ended.complete(CloseInfo(code, reason))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val code = response?.code ?: -1
                        ended.complete(CloseInfo(code, "Discord Gateway connection failed"))
                    }
                }

                val close = try {
                    val socket = http.newWebSocket(Request.Builder().url(gatewayUrl).build(), listener)
                    socketRef.compareAndSet(null, socket)
                    ended.await()
                } finally {
                    heartbeatRef.getAndSet(null)?.cancel()
                    socketRef.getAndSet(null)?.cancel()
                }
                when (close.code) {
                    4004 -> throw IllegalStateException("Discord rejected the bot token. Reconnect with a valid token.")
                    4014 -> throw IllegalStateException("Discord denied the requested Gateway intents. Enable Message Content Intent in the Discord Developer Portal, then reconnect.")
                }
                if (manager.isEnabled()) {
                    failures++
                    manager.setConnected(false, "Discord disconnected. Reconnecting shortly.")
                    delay(reconnectDelay(failures))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                val safeMessage = e.message?.takeIf { it.contains("Gateway intents") || it.contains("token") }
                    ?: "Discord connection failed. Check network access and bot permissions."
                manager.setConnected(false, safeMessage)
                if (manager.isEnabled()) delay(reconnectDelay(failures))
            }
        }
    }

    private fun isOneToOneDm(token: String, channelId: String): Boolean {
        if (!channelId.matches(Regex("[0-9]{17,20}"))) return false
        val request = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId")
            .header("Authorization", "Bot $token")
            .header("User-Agent", "AndroidHarnessRemake")
            .get()
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                response.isSuccessful && JSONObject(response.body?.string().orEmpty()).optInt("type", -1) == 1
            }
        }.getOrDefault(false)
    }

    private fun heartbeat(sequence: Long) = JSONObject()
        .put("op", 1)
        .put("d", if (sequence >= 0) sequence else JSONObject.NULL)
        .toString()

    private fun reconnectDelay(failures: Int) = (1_000L shl failures.coerceIn(0, 5)).coerceAtMost(30_000L)

    private data class CloseInfo(val code: Int, val reason: String)

    private companion object {
        const val INTENT_GUILD_MESSAGES = 1 shl 9
        const val INTENT_DIRECT_MESSAGES = 1 shl 12
        const val INTENT_MESSAGE_CONTENT = 1 shl 15
    }
}
