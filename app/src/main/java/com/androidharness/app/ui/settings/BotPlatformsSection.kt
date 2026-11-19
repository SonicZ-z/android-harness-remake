package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.ui.theme.AuroraPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DiscordSettingsSection(container: AppContainer, onOpenBackgroundSettings: () -> Unit) {
    val manager = container.discordBots
    val setup by manager.setup.collectAsStateWithLifecycle()
    val runtime by manager.runtime.collectAsStateWithLifecycle()
    val error by manager.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var ownerId by remember { mutableStateOf("") }
    var prefix by remember(setup.commandPrefix) { mutableStateOf(setup.commandPrefix) }
    var aiRepliesEnabled by remember(setup.aiRepliesEnabled) { mutableStateOf(setup.aiRepliesEnabled) }
    var customCommandsEnabled by remember(setup.customCommandsEnabled) { mutableStateOf(setup.customCommandsEnabled) }
    var aiBaseUrl by remember(setup.aiBaseUrl) { mutableStateOf(setup.aiBaseUrl) }
    var aiModel by remember(setup.aiModel) { mutableStateOf(setup.aiModel) }
    var aiApiKey by remember { mutableStateOf("") }
    var commandName by remember { mutableStateOf("") }
    var commandSource by remember { mutableStateOf("import sys\n\ntext = sys.stdin.read().strip()\nprint(f\"You sent: {text}\")\n") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun runAction(success: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        actionError = null
        scope.launch {
            try {
                block()
                notice = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = e.message ?: "Could not complete this action."
            } finally {
                busy = false
            }
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Delete Python command?") },
            text = { Text("${setup.commandPrefix}$name will be removed from the command list and deleted from this device.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingDelete = null
                    runAction("Command deleted.") {
                        manager.removeCustomCommand(name)
                        if (commandName == name) {
                            commandName = ""
                            commandSource = ""
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        AuroraPanel(Modifier.fillMaxWidth()) {
            Text("Discord bot", style = MaterialTheme.typography.titleSmall)
            Text(
                "Only the configured owner can control the bot. Owner DMs can start an AI conversation; in a server, an owner message must tag the bot. Server replies go privately to your Discord account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Discord bot token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = ownerId.ifBlank { setup.ownerId.orEmpty() },
                onValueChange = { ownerId = it.filter(Char::isDigit).take(20) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Allowed owner Discord user ID") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && token.isNotBlank() && ownerId.ifBlank { setup.ownerId.orEmpty() }.isNotBlank(),
                    onClick = {
                        runAction("Discord bot connected. Review the intents note before enabling it.") {
                            manager.connect(token, ownerId.ifBlank { setup.ownerId.orEmpty() })
                            token = ""
                        }
                    },
                ) { Text(if (setup.botName == null) "Validate and save" else "Replace token") }
                if (setup.botName != null) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            runAction(if (setup.enabled) "Discord bot paused." else "Discord bot enabled.") {
                                if (setup.enabled) manager.pause() else manager.enable()
                            }
                        },
                    ) { Text(if (setup.enabled) "Pause" else "Start") }
                }
            }
            if (setup.botName != null) {
                Text("Bot: ${setup.botName} · owner ID: ${setup.ownerId}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (setup.enabled) runtime.message ?: if (runtime.connected) "Connected" else "Connecting…" else "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (runtime.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "AI uses the endpoint configured below. It has no app tools and cannot run commands or edit files through chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(enabled = !busy, onClick = {
                    runAction("Discord bot disconnected.") { manager.disconnect(); ownerId = ""; aiApiKey = "" }
                }) { Text("Disconnect Discord") }
            }
            Text(
                "In the Discord Developer Portal, enable Message Content Intent and invite the bot with view/send message permissions. Tokens are stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "If background running is off, the bot temporarily pauses when you leave the app and resumes when you return. Android battery restrictions can still affect background activity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenBackgroundSettings) { Text("Open background settings") }
            (actionError ?: error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }

        AuroraPanel(Modifier.fillMaxWidth()) {
            Text("AI conversation", style = MaterialTheme.typography.titleSmall)
            Text(
                "Use an OpenAI-compatible Chat Completions API. Recent turns are saved with the encrypted Discord settings. Your configured endpoint receives the conversation text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it.take(2048) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AI API base URL") },
                supportingText = { Text("Example: https://api.openai.com/v1. Use HTTPS except for localhost services.") },
                singleLine = true,
            )
            OutlinedTextField(
                value = aiModel,
                onValueChange = { aiModel = it.take(128) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AI model") },
                singleLine = true,
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = { aiApiKey = it.take(2048) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AI API key") },
                supportingText = { Text(if (setup.aiApiKeyConfigured) "A key is saved securely. Leave blank to keep it; it is never shown again." else "The key is stored encrypted on this device and is never sent to Discord.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy, onClick = {
                    runAction("AI settings saved. Tag the bot in a server or message it privately to start a conversation.") {
                        manager.updateAiSettings(aiBaseUrl, aiModel, aiApiKey)
                        aiApiKey = ""
                    }
                }) { Text("Save AI settings") }
                if (setup.aiApiKeyConfigured) {
                    TextButton(enabled = !busy, onClick = {
                        runAction("AI API key and conversation history cleared.") {
                            manager.clearAiApiKey()
                            aiApiKey = ""
                        }
                    }) { Text("Clear key") }
                }
            }
            BotOptionSwitch(
                title = "AI replies",
                description = "Owner DMs can start a chat. In servers, only an owner message tagging the bot triggers an AI reply.",
                checked = aiRepliesEnabled,
                enabled = !busy,
                onCheckedChange = { aiRepliesEnabled = it },
            )
            BotOptionSwitch(
                title = "Custom Python commands",
                description = "Allow only saved named scripts from the Command Manager below.",
                checked = customCommandsEnabled,
                enabled = !busy,
                onCheckedChange = { customCommandsEnabled = it },
            )
            Button(enabled = !busy, onClick = {
                runAction("Bot options saved.") {
                    manager.updateOptions(prefix, aiRepliesEnabled, customCommandsEnabled)
                }
            }) { Text("Save bot options") }
        }

        AuroraPanel(Modifier.fillMaxWidth()) {
            Text("Command Manager", style = MaterialTheme.typography.titleSmall)
            Text(
                "Add, edit, and delete named Python commands here. Run one with ${setup.commandPrefix}run <name> [input], or use ${setup.commandPrefix}<name> [input]. Messages cannot supply shell commands or Python source. Each saved script has a 15 second limit and bounded reply output, but scripts run as the app's Linux user and can access files and services available to that user. Add only code you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = commandName,
                onValueChange = { commandName = it.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' }.take(24) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Command name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = commandSource,
                onValueChange = { commandSource = it.take(30_000) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Python source") },
                minLines = 6,
                maxLines = 12,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && commandName.isNotBlank() && commandSource.isNotBlank(),
                    onClick = {
                        val replacing = setup.customCommands.contains(commandName.trim().lowercase())
                        runAction(if (replacing) "Python command updated." else "Python command added.") {
                            manager.saveCustomCommand(commandName, commandSource)
                            commandName = ""
                        }
                    },
                ) { Text(if (setup.customCommands.contains(commandName.trim().lowercase())) "Update command" else "Add command") }
                if (commandName.isNotBlank()) {
                    TextButton(enabled = !busy, onClick = { commandName = ""; commandSource = "" }) { Text("Clear editor") }
                }
            }
            Text("${setup.customCommands.size} saved command${if (setup.customCommands.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
            if (setup.customCommands.isEmpty()) {
                Text("No saved commands yet. Enter a name and Python source above, then choose Add command.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            setup.customCommands.forEach { name ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${setup.commandPrefix}$name [input]", style = MaterialTheme.typography.bodyMedium)
                        Text("Python command", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !busy, onClick = {
                        runAction("Command loaded for editing.") {
                            commandName = name
                            commandSource = manager.loadCustomCommand(name)
                        }
                    }) { Text("Edit") }
                    TextButton(enabled = !busy, onClick = { pendingDelete = name }) { Text("Delete") }
                }
            }
            Text(
                "The list is an allowlist, not a remote shell. Python becomes available after the app's Linux environment is installed. Turn off Custom Python commands above to block all Discord-triggered script runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BotOptionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
