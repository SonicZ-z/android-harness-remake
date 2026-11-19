package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.telegram.DiscordBotManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BotPlatformsSection(container: AppContainer) {
    val manager = container.discordBots
    val setup by manager.setup.collectAsStateWithLifecycle()
    val runtime by manager.runtime.collectAsStateWithLifecycle()
    val error by manager.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var ownerId by remember { mutableStateOf("") }
    var commandName by remember { mutableStateOf("") }
    var commandSource by remember { mutableStateOf("import sys\n\ntext = sys.stdin.read().strip()\nprint(f\"You sent: {text}\")\n") }
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text("More bot platforms", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Discord bot", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Use a Discord application bot token and your Discord numeric user ID. Only that owner account is accepted. Mention the bot in a server or message it privately for an AI conversation. Server replies are delivered to your private Discord chat.",
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
                    Text("AI requests use Plan mode and cannot edit files through Discord.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(enabled = !busy, onClick = { runAction("Discord bot disconnected.") { manager.disconnect(); ownerId = "" } }) {
                        Text("Disconnect Discord")
                    }
                }
                Text(
                    "In the Discord Developer Portal, enable Message Content Intent and invite the bot with view/send message permissions. Never share the token. It is stored encrypted on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (actionError ?: error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Custom Python commands", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Add named scripts here, then run them with !run <name> [input]. Messages cannot supply shell commands or Python code. Scripts run in the app's own Linux user environment with a 15 second limit. Only add code you trust; a script can access app-private files available to that user.",
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
                Button(
                    enabled = !busy && commandName.isNotBlank() && commandSource.isNotBlank(),
                    onClick = {
                        runAction("Python command saved.") {
                            manager.saveCustomCommand(commandName, commandSource)
                            commandName = ""
                        }
                    },
                ) { Text("Save command") }
                setup.customCommands.forEach { name ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("!run $name [input]", modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        TextButton(enabled = !busy, onClick = { runAction("Command removed.") { manager.removeCustomCommand(name) } }) {
                            Text("Remove")
                        }
                    }
                }
                Text(
                    "This is an allowlist of locally authored scripts, not a remote shell. Python is available only after the app's Linux environment is installed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WeChat", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Not available as a phone-local bot in this build. Official WeChat bot integrations require an eligible Official Account and a publicly reachable HTTPS callback service. A local Android app cannot receive those inbound callbacks by itself. Do not enter a WeChat password or token here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
