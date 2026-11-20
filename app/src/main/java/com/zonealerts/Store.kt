package com.zonealerts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Zone(val id: String, val index: String, val lower: Double, val upper: Double,
                val enabled: Boolean = true, val previousPrice: Double? = null)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("zone_alerts", Context.MODE_PRIVATE)
    @Synchronized fun zones(c: Context): List<Zone> {
        val a = JSONArray(prefs(c).getString("zones", "[]") ?: "[]")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Zone(o.getString("id"), o.getString("index"), o.getDouble("lower"),
                o.getDouble("upper"), o.optBoolean("enabled", true),
                if (o.isNull("previousPrice") || !o.has("previousPrice")) null else o.getDouble("previousPrice"))
        }
    }
    @Synchronized fun saveZones(c: Context, list: List<Zone>) {
        val a = JSONArray()
        list.forEach { z -> a.put(JSONObject().put("id", z.id).put("index", z.index)
            .put("lower", z.lower).put("upper", z.upper).put("enabled", z.enabled)
            .put("previousPrice", z.previousPrice ?: JSONObject.NULL)) }
        prefs(c).edit().putString("zones", a.toString()).commit()
    }
    @Synchronized fun addZone(c: Context, index: String, lower: Double, upper: Double) {
        require(lower.isFinite() && upper.isFinite() && lower < upper)
        saveZones(c, zones(c) + Zone(UUID.randomUUID().toString(), index, lower, upper))
    }
    @Synchronized fun toggleZone(c: Context, id: String) = saveZones(c, zones(c).map {
        if (it.id == id) it.copy(enabled = !it.enabled, previousPrice = null) else it
    })
    @Synchronized fun deleteZone(c: Context, id: String) = saveZones(c, zones(c).filter { it.id != id })
    @Synchronized fun updatePrevious(c: Context, id: String, price: Double) = saveZones(c, zones(c).map {
        if (it.id == id) it.copy(previousPrice = price) else it
    })
    fun isRunning(c: Context) = prefs(c).getBoolean("running", false)
    fun setRunning(c: Context, value: Boolean) { prefs(c).edit().putBoolean("running", value).apply() }
    fun setStatus(c: Context, value: String) { prefs(c).edit().putString("status", value).apply() }
    fun status(c: Context) = prefs(c).getString("status", "Stopped") ?: "Stopped"
    fun setLastCheck(c: Context, value: String) { prefs(c).edit().putString("last_check", value).apply() }
    fun lastCheck(c: Context) = prefs(c).getString("last_check", "Never") ?: "Never"
    fun saveTelegram(c: Context, token: String, chatId: String) {
        prefs(c).edit().putString("telegram_token", token.trim()).putString("telegram_chat", chatId.trim()).apply()
    }
    fun telegramToken(c: Context) = prefs(c).getString("telegram_token", "") ?: ""
    fun telegramChat(c: Context) = prefs(c).getString("telegram_chat", "") ?: ""
    @Synchronized fun log(c: Context, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val current = logs(c)
        val next = (listOf("$timestamp  $message") + current).take(40)
        prefs(c).edit().putString("logs", JSONArray(next).toString()).apply()
    }
    fun logs(c: Context): List<String> {
        val a = JSONArray(prefs(c).getString("logs", "[]") ?: "[]")
        return (0 until a.length()).map { a.getString(it) }
    }
    fun cooldownKey(zoneId: String, event: String) = "cooldown_${zoneId}_$event"
    fun lastAlert(c: Context, zoneId: String, event: String) =
        prefs(c).getLong(cooldownKey(zoneId, event), 0L)
    fun recordAlert(c: Context, zoneId: String, event: String, time: Long) {
        prefs(c).edit().putLong(cooldownKey(zoneId, event), time).apply()
    }
}
