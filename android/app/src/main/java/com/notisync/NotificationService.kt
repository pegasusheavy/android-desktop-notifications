package com.notisync

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationService"
        var instance: NotificationService? = null
            private set
    }

    private var connectionManager: ConnectionManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        connectionManager = ConnectionManager(this)

        connectionManager?.listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {
                Log.d(TAG, "Connected to $hostName")
            }

            override fun onDisconnected() {
                Log.d(TAG, "Disconnected")
            }

            override fun onSearching() {
                Log.d(TAG, "Searching for desktop...")
            }
        }

        Log.d(TAG, "NotificationService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")

        if (isEnabled()) {
            connectionManager?.start()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        connectionManager?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
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
            connectionManager?.start()
        } else {
            connectionManager?.stop()
        }
    }
}
