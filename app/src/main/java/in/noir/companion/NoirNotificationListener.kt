package com.noir.companion

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/** Mirrors notification metadata locally after the user explicitly enables notification access. */
class NoirNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val prefs = getSharedPreferences("noir_notifications", MODE_PRIVATE)
        val list = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        val extras = sbn.notification.extras
        val item = JSONObject()
        item.put("package", sbn.packageName)
        item.put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Notification")
        item.put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")
        item.put("time", sbn.postTime)
        val next = JSONArray()
        next.put(item)
        for (i in 0 until minOf(list.length(), 49)) {
            val old = list.optJSONObject(i) ?: continue
            if (old.optLong("time") != sbn.postTime || old.optString("package") != sbn.packageName) next.put(old)
        }
        prefs.edit().putString("items", next.toString()).apply()
    }
}
