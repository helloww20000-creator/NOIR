package com.noir.companion

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Receives phone notification events for the future watch-forwarding transport. */
class NoirNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        // Store metadata only. Forwarding to the watch is intentionally gated until
        // the exact model's notification packet format is verified.
        getSharedPreferences("noir_notifications", MODE_PRIVATE).edit()
            .putString("last_package", sbn.packageName)
            .putString("last_title", title.take(200))
            .putString("last_text", text.take(500))
            .putLong("last_when", sbn.postTime)
            .apply()
    }
}