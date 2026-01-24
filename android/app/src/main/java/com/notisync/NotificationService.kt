package com.notisync

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Will be implemented in next task
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for display-only
    }
}
