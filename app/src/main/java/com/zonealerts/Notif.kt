package com.zonealerts

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

object Notif {
    const val ALERT_CHANNEL = "zone_alerts_high"
    const val SERVICE_CHANNEL = "zone_alerts_service"
    const val SERVICE_ID = 1001
    private val nextId = AtomicInteger(2000)
    fun createChannels(c: Context) {
        val m = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alert = NotificationChannel(ALERT_CHANNEL, "Zone alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Price zone entry and exit alerts"
            enableVibration(true)
            enableLights(true)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
        }
        val service = NotificationChannel(SERVICE_CHANNEL, "Monitoring status", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        }
        m.createNotificationChannel(alert)
        m.createNotificationChannel(service)
    }
    private fun tap(c: Context): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun serviceNotification(c: Context, status: String): Notification = Notification.Builder(c, SERVICE_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Zone Alerts monitoring")
        .setContentText(status).setContentIntent(tap(c)).setOngoing(true).setOnlyAlertOnce(true).build()
    fun updateService(c: Context, status: String) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(SERVICE_ID, serviceNotification(c, status))
    }
    fun alert(c: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Store.log(c, "Notification permission not granted")
            return
        }
        val n = Notification.Builder(c, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title)
            .setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(tap(c)).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_HIGH).build()
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(nextId.incrementAndGet(), n)
    }
    // Call from a worker thread, never from the main UI thread.
    fun sendTelegram(c: Context, message: String) {
        val token = Store.telegramToken(c)
        val chat = Store.telegramChat(c)
        if (token.isBlank() || chat.isBlank()) {
            Store.log(c, "Telegram not configured: save bot token and chat ID")
            return
        }
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val data = JSONObject().put("chat_id", chat).put("text", message).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(data) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            val result = try { JSONObject(response) } catch (_: Exception) { JSONObject() }
            val success = code in 200..299 && result.optBoolean("ok", false)
            val description = result.optString("description", "unknown response")
            Store.log(c, if (success) "Telegram message sent" else "Telegram failed: HTTP $code ($description)")
        } catch (e: Exception) {
            Store.log(c, "Telegram error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        } finally { connection?.disconnect() }
    }
}
