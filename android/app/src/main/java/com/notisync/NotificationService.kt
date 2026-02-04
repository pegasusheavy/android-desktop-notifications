package com.notisync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "notisync_service"
        private const val NOTIFICATION_ID = 1001
        var instance: NotificationService? = null
            private set
    }

    private var connectionManager: ConnectionManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        createNotificationChannel()
        connectionManager = ConnectionManager(this)

        connectionManager?.listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {
                Log.d(TAG, "Connected to $hostName")
                updateForegroundNotification("Connected to $hostName")
            }

            override fun onDisconnected() {
                Log.d(TAG, "Disconnected")
                updateForegroundNotification("Searching for desktop...")
            }

            override fun onSearching() {
                Log.d(TAG, "Searching for desktop...")
                updateForegroundNotification("Searching for desktop...")
            }
        }

        Log.d(TAG, "NotificationService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")

        if (isEnabled()) {
            startForegroundService()
            connectionManager?.start()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        stopForegroundService()
        connectionManager?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
        connectionManager?.stop()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isEnabled()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        // Skip our own notifications
        if (sbn.packageName == packageName) return

        // Skip ongoing notifications (media players, etc.)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val appName = getAppName(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Skip if no content
        if (title == null && text == null) return

        val phoneNotification = PhoneNotification(
            id = sbn.key,
            app_package = sbn.packageName,
            app_name = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        Log.d(TAG, "Notification from $appName: $title")
        connectionManager?.sendNotification(phoneNotification)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for display-only mode
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NotiSync Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps NotiSync running to sync notifications"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotiSync")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startForegroundService() {
        if (isForeground) return
        
        val notification = createForegroundNotification("Starting...")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            acquireWakeLock()
            Log.d(TAG, "Started foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        if (!isForeground) return
        
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        Log.d(TAG, "Stopped foreground service")
    }

    private fun updateForegroundNotification(status: String) {
        if (!isForeground) return
        
        val notification = createForegroundNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotiSync::SyncWakeLock"
        ).apply {
            acquire()
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isEnabled(): Boolean {
        return getSharedPreferences("notisync", MODE_PRIVATE)
            .getBoolean("enabled", false)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            startForegroundService()
            connectionManager?.start()
        } else {
            connectionManager?.stop()
            stopForegroundService()
        }
    }
}
