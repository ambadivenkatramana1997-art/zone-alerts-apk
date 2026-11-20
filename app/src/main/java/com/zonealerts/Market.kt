package com.zonealerts

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

class StaleDataException(message: String) : Exception(message)
data class MarketPrice(val price: Double, val timestamp: Long, val ageSeconds: Long)

object Market {
    fun isOpen(): Boolean {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = now.toLocalTime()
        return !t.isBefore(LocalTime.of(9, 15)) && t.isBefore(LocalTime.of(15, 30))
    }
    fun fetch(index: String): MarketPrice {
        val symbol = when (index) {
            "NIFTY 50" -> "%5ENSEI"
            "BANK NIFTY" -> "%5ENSEBANK"
            "SENSEX" -> "%5EBSESN"
            else -> throw IllegalArgumentException("Unknown index: $index")
        }
        val connection = URL("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1m&range=1d")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            if (connection.responseCode !in 200..299) throw Exception("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val price = meta.getDouble("regularMarketPrice")
            val timestamp = meta.getLong("regularMarketTime")
            val age = (System.currentTimeMillis() / 1000L - timestamp).coerceAtLeast(0L)
            if (!price.isFinite() || price <= 0) throw Exception("Invalid price")
            if (age > 300L) throw StaleDataException("Stale data: $index age ${age}s")
            return MarketPrice(price, timestamp, age)
        } finally { connection.disconnect() }
    }
}
