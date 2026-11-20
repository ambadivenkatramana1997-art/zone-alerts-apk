package com.zonealerts

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

class MonitorService : Service() {
    companion object {
        const val ACTION_START = "com.zonealerts.START"
        const val ACTION_STOP = "com.zonealerts.STOP"
        private const val POLL_MS = 10_000L
        private const val COOLDOWN_MS = 120_000L
    }
    private val monitoring = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); Notif.createChannels(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        startForeground(Notif.SERVICE_ID, Notif.serviceNotification(this, "Starting monitoring..."))
        if (monitoring.compareAndSet(false, true)) {
            Store.setRunning(this, true)
            Store.setStatus(this, "Starting")
            Store.log(this, "Monitoring service started")
            acquireWakeLock()
            worker = Thread({ monitoringLoop() }, "ZoneAlertsMonitor").apply { start() }
        }
        return START_STICKY
    }
    private fun acquireWakeLock() {
        try {
            val m = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = m.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZoneAlerts:MonitoringWakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) { Store.log(this, "Wake lock error: ${e.message}") }
    }
    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        wakeLock = null
    }
    private fun timeNow() = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }.format(Date())
    private fun updateStatus(s: String) {
        Store.setStatus(this, s)
        Store.setLastCheck(this, timeNow())
        try { Notif.updateService(this, "$s | ${timeNow()} IST") }
        catch (e: Exception) { Store.log(this, "Notification error: ${e.message}") }
    }
    private fun monitoringLoop() {
        try {
            while (monitoring.get()) {
                val start = SystemClock.elapsedRealtime()
                try {
                    if (!Market.isOpen()) updateStatus("Market closed") else pollMarkets()
                } catch (e: Exception) {
                    Store.log(this, "Monitoring error: ${e.javaClass.simpleName}: ${e.message}")
                    updateStatus("Recovering from error")
                }
                val wait = (POLL_MS - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0L)
                if (wait > 0 && monitoring.get()) try { Thread.sleep(wait) }
                catch (_: InterruptedException) { if (!monitoring.get()) break }
            }
        } finally { releaseWakeLock() }
    }
    private fun pollMarkets() {
        val active = Store.zones(this).filter { it.enabled }
        if (active.isEmpty()) { updateStatus("Running - no active zones"); return }
        for (index in active.map { it.index }.distinct()) {
            if (!monitoring.get()) return
            try {
                val quote = Market.fetch(index)
                Store.log(this, "$index: ${fmt(quote.price)} (age ${quote.ageSeconds}s)")
                evaluateZones(index, quote.price)
            } catch (e: StaleDataException) { Store.log(this, e.message ?: "Stale data: $index") }
            catch (e: Exception) { Store.log(this, "$index network error: ${e.javaClass.simpleName}: ${e.message}") }
        }
        updateStatus("Monitoring active")
    }
    private fun evaluateZones(index: String, price: Double) {
        for (z in Store.zones(this).filter { it.enabled && it.index == index }) {
            if (!monitoring.get()) return
            val previous = z.previousPrice
            if (previous == null) {
                Store.updatePrevious(this, z.id, price)
                Store.log(this, "$index baseline: ${fmt(price)}")
                continue
            }
            val wasInside = previous >= z.lower && previous <= z.upper
            val isInside = price >= z.lower && price <= z.upper
            val event = when {
                !wasInside && isInside -> "ENTER"
                wasInside && price > z.upper -> "EXIT_UP"
                wasInside && price < z.lower -> "EXIT_DOWN"
                else -> null
            }
            val title = when (event) {
                "ENTER" -> "Entered zone"
                "EXIT_UP" -> "Exited zone - upside move"
                "EXIT_DOWN" -> "Exited zone - downside move"
                else -> null
            }
            if (event != null && title != null) dispatch(z, price, event, title)
            Store.updatePrevious(this, z.id, price)
        }
    }
    private fun dispatch(z: Zone, price: Double, event: String, eventTitle: String) {
        val now = System.currentTimeMillis()
        val last = Store.lastAlert(this, z.id, event)
        if (last > 0 && now - last < COOLDOWN_MS) {
            Store.log(this, "${z.index} $event suppressed (cooldown)")
            return
        }
        val title = "${z.index} $eventTitle"
        val msg = "Zone: ${fmt(z.lower)} - ${fmt(z.upper)} | Current: ${fmt(price)}"
        Store.recordAlert(this, z.id, event, now)
        Store.log(this, "ALERT: $title | $msg")
        try { Notif.alert(this, title, msg) }
        catch (e: Exception) { Store.log(this, "Alert error: ${e.message}") }
        Notif.sendTelegram(this, "$title\n$msg")
    }
    private fun fmt(n: Double) = String.format(Locale.US, "%,.2f", n)
    private fun stopMonitoring() {
        Store.setRunning(this, false)
        Store.setStatus(this, "Stopped")
        Store.log(this, "Monitoring stopped")
        monitoring.set(false)
        worker?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        monitoring.set(false)
        worker?.interrupt()
        releaseWakeLock()
        super.onDestroy()
    }
}
