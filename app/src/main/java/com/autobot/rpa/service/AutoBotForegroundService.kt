package com.autobot.rpa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.autobot.rpa.MainActivity
import com.autobot.rpa.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoBotForegroundService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "autobot_automation_channel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_SERVICE_TYPE = "service_type"
        const val SERVICE_TYPE_AUTOMATION = 0
        const val SERVICE_TYPE_MEDIA_PROJECTION = 1

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _currentScriptName = MutableStateFlow("")
        val currentScriptName: StateFlow<String> = _currentScriptName

        private var instance: AutoBotForegroundService? = null

        fun startService(context: Context, serviceType: Int = SERVICE_TYPE_AUTOMATION) {
            val intent = Intent(context, AutoBotForegroundService::class.java).apply {
                putExtra(EXTRA_SERVICE_TYPE, serviceType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AutoBotForegroundService::class.java)
            context.stopService(intent)
        }

        fun updateNotification(scriptName: String) {
            instance?.updateNotificationInternal(scriptName)
        }
    }

    private var currentServiceType = SERVICE_TYPE_AUTOMATION

    inner class LocalBinder : Binder() {
        fun getService(): AutoBotForegroundService = this@AutoBotForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // Start foreground immediately when service is created to prevent timeout
        startForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isRunning.value = false
        _currentScriptName.value = ""
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newServiceType = intent?.getIntExtra(EXTRA_SERVICE_TYPE, SERVICE_TYPE_AUTOMATION) ?: SERVICE_TYPE_AUTOMATION
        
        // If service type changed, update and restart foreground
        if (newServiceType != currentServiceType) {
            currentServiceType = newServiceType
            startForeground()
        }
        
        _isRunning.value = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = when (currentServiceType) {
                SERVICE_TYPE_MEDIA_PROJECTION -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                SERVICE_TYPE_AUTOMATION -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (currentServiceType == SERVICE_TYPE_MEDIA_PROJECTION) {
            "Screen capture active"
        } else {
            getString(R.string.automation_running)
        }

        val text = if (currentServiceType == SERVICE_TYPE_MEDIA_PROJECTION) {
            "AutoBot is capturing your screen"
        } else {
            _currentScriptName.value.ifEmpty { "AutoBot RPA" }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationInternal(scriptName: String) {
        _currentScriptName.value = scriptName
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateNotification(scriptName: String) {
        updateNotificationInternal(scriptName)
    }
}
