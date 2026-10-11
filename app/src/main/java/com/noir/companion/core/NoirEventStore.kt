package com.noir.companion.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local diagnostic history; no account or cloud service required. */
class NoirEventStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("noir_event_store", Context.MODE_PRIVATE)
    @Synchronized fun append(event: BleDeviceRepository.Event) {
        val array = JSONArray(prefs.getString("events", "[]"))
        array.put(JSONObject().put("timestamp", event.timestamp).put("category", event.category).put("message", event.message))
        while (array.length() > MAX_EVENTS) array.remove(0)
        prefs.edit().putString("events", array.toString()).apply()
    }
    fun exportText(): String {
        val array = JSONArray(prefs.getString("events", "[]"))
        return (0 until array.length()).joinToString("\n") { i ->
            val e = array.getJSONObject(i)
            "[${e.optString("timestamp")}] ${e.optString("category")}: ${e.optString("message")}"
        }
    }
    fun clear() { prefs.edit().remove("events").apply() }
    companion object { private const val MAX_EVENTS = 1500 }
}
