package com.androidharness.app.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.androidharness.app.AppNotifications
import com.androidharness.app.HarnessApp
import com.androidharness.app.MainActivity
import com.androidharness.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DiscordBotService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: DiscordBotManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        manager = (application as HarnessApp).container.discordBots
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Discord bot", NotificationManager.IMPORTANCE_LOW),
        )
        val initial = notification("Starting…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, initial)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "harness:discord").apply {
                setReferenceCounted(false)
                acquire(600_000)
            }
        scope.launch {
            while (isActive) {
                delay(540_000)
                wakeLock?.acquire(600_000)
            }
        }
        scope.launch {
            combine(manager.setup, manager.runtime, manager.error) { setup, runtime, error ->
                when {
                    !setup.enabled -> error ?: "Paused"
                    runtime.connected -> "Connected to Discord"
                    runtime.message != null -> runtime.message
                    else -> "Connecting…"
                }
            }.collect { text ->
                AppNotifications.post(this@DiscordBotService, ID, notification(text))
                if (!manager.setup.value.enabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE || intent?.action == ACTION_STOP) {
            scope.launch { manager.pause(); stopSelf() }
            return START_NOT_STICKY
        }
        if (!manager.setup.value.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if ((application as HarnessApp).container.runtimePolicy.state.value.let { it.ready && !it.canRun }) {
            stopSelf()
            return START_NOT_STICKY
        }
        manager.startRuntime(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        manager.launch { manager.serviceDestroyed(this@DiscordBotService) }
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(ID)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = Intent(this, MainActivity::class.java)
            .putExtra("settings_page", "discord")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Android Harness Remake · Discord")
            .setContentText(text.take(180))
            .setContentIntent(PendingIntent.getActivity(this, ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        listOf("Pause" to ACTION_PAUSE, "Stop bot" to ACTION_STOP).forEachIndexed { index, (label, action) ->
            val pending = PendingIntent.getService(this, ID + index + 1,
                Intent(this, DiscordBotService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, label, pending).build())
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "discord_bot"
        private const val ID = 9150
        private const val ACTION_PAUSE = "com.androidharness.app.discord.PAUSE"
        private const val ACTION_STOP = "com.androidharness.app.discord.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DiscordBotService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DiscordBotService::class.java))
        }
    }
}

internal class DiscordBotBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        (context.applicationContext as? HarnessApp)?.container?.discordBots?.restoreIfEnabled()
    }
}
