package com.zonealerts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button
    private lateinit var zoneContainer: LinearLayout
    private lateinit var logsText: TextView
    private lateinit var indexSpinner: Spinner
    private lateinit var lowerInput: EditText
    private lateinit var upperInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var chatInput: EditText
    private val navy = Color.rgb(14, 25, 48)
    private val blue = Color.rgb(59, 130, 246)
    private val muted = Color.rgb(110, 126, 150)
    private val surface = Color.rgb(19, 34, 60)
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!visible) return
            refreshUi()
            handler.postDelayed(this, 3000L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notif.createChannels(this)
        createUi()
        requestNotificationPermission()
        refreshUi()
    }
    override fun onResume() {
        super.onResume()
        visible = true
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }
    override fun onPause() {
        visible = false
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun panel() = column().apply {
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = bg(surface, 22, Color.rgb(36, 54, 83))
    }
    private fun section(label: String, subtitle: String, body: LinearLayout) {
        add(root, heading(label))
        add(root, text(subtitle, 12f).apply { setTextColor(muted) })
        val params = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        root.addView(body, params)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 15f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(230, 237, 250))
        setPadding(0, dp(5), 0, dp(5))
    }
    private fun heading(value: String) = text(value, 19f).apply {
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(8))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        background = bg(blue, 14)
        minHeight = dp(48)
        setOnClickListener { action() }
    }
    private fun input(hintText: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        hint = hintText
        inputType = type
        textSize = 15f
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(muted)
        background = bg(navy, 12, Color.rgb(48, 66, 95))
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }
    private fun add(parent: LinearLayout, child: View) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2))
    }
    private fun createUi() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(navy)
        }
        root = column().apply { setPadding(dp(18), dp(24), dp(18), dp(40)) }
        scroll.addView(root)
        setContentView(scroll)
        val hero = column().apply {
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(36, 86, 180), Color.rgb(14, 39, 85))).apply { cornerRadius = dp(24).toFloat() }
        }
        add(hero, text("ZONE ALERTS  •  LIVE DASHBOARD", 12f).apply { setTypeface(null, Typeface.BOLD) })
        add(hero, text("Trade zones. Stay ahead.", 24f).apply { setTypeface(null, Typeface.BOLD) })
        add(hero, text("NIFTY 50  •  BANK NIFTY  •  SENSEX", 12f))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))
        add(root, heading("Monitoring overview"))
        val monitoringPanel = panel()
        statusText = text("Loading status...", 14f)
        add(monitoringPanel, statusText)
        startStopButton = button("Start monitoring") {
            if (Store.isRunning(this)) stopMonitoring() else startMonitoring()
        }
        add(monitoringPanel, startStopButton)
        add(monitoringPanel, button("Allow background running") { requestBatteryExemption() })
        add(monitoringPanel, button("Send test notification") {
            Notif.alert(this, "NIFTY 50 Test alert", "Zone: 25,400.00 - 25,500.00 | Current: 25,450.00")
            Store.log(this, "Test notification requested")
            refreshUi()
        })
        add(root, monitoringPanel)
        add(root, heading("Create a price zone"))
        val addPanel = panel()
        indexSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("NIFTY 50", "BANK NIFTY", "SENSEX"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        indexSpinner.adapter = adapter
        add(addPanel, indexSpinner)
        val numberType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        lowerInput = input("Lower price (example: 25400)", numberType)
        upperInput = input("Upper price (example: 25500)", numberType)
        add(addPanel, lowerInput)
        add(addPanel, upperInput)
        add(addPanel, button("Add zone") { addZone() })
        add(root, addPanel)
        add(root, heading("Your zones"))
        zoneContainer = column()
        add(root, zoneContainer)
        add(root, heading("Telegram alerts"))
        val telegramPanel = panel()
        add(telegramPanel, text("Connect your bot to receive instant zone alerts.", 13f))
        tokenInput = input("Telegram bot token").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Store.telegramToken(this@MainActivity))
        }
        chatInput = input("Telegram chat ID").apply { setText(Store.telegramChat(this@MainActivity)) }
        add(telegramPanel, tokenInput)
        add(telegramPanel, chatInput)
        add(telegramPanel, button("Save Telegram settings") {
            Store.saveTelegram(this, tokenInput.text.toString(), chatInput.text.toString())
            Store.log(this, "Telegram settings updated")
            toast("Telegram settings saved")
            refreshUi()
        })
        add(telegramPanel, button("Send Telegram test") {
            Store.saveTelegram(this, tokenInput.text.toString(), chatInput.text.toString())
            if (Store.telegramToken(this).isBlank() || Store.telegramChat(this).isBlank()) {
                toast("Enter bot token and chat ID first")
            } else {
                toast("Sending Telegram test...")
                Thread({
                    Notif.sendTelegram(this, "Zone Alerts Android Telegram test")
                    runOnUiThread { refreshUi() }
                }, "TelegramTest").start()
            }
        })
        add(root, telegramPanel)
        add(root, heading("Activity log"))
        logsText = text("No logs yet", 12f).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = bg(surface, 18)
        }
        add(root, logsText)
        add(root, text("Market hours: Mon-Fri, 09:15-15:30 IST\nPolling: every 10 seconds\nStale quote limit: 300 seconds\nAlert cooldown: 120 seconds per event", 12f))
    }
    private fun addZone() {
        val lower = lowerInput.text.toString().trim().toDoubleOrNull()
        val upper = upperInput.text.toString().trim().toDoubleOrNull()
        if (lower == null || upper == null || !lower.isFinite() || !upper.isFinite() || lower >= upper) {
            toast("Enter valid prices: lower must be less than upper")
            return
        }
        try {
            Store.addZone(this, indexSpinner.selectedItem.toString(), lower, upper)
            lowerInput.text.clear()
            upperInput.text.clear()
            toast("Zone added")
            refreshUi()
        } catch (e: Exception) { toast("Could not add zone: ${e.message}") }
    }
    private fun refreshUi() {
        val running = Store.isRunning(this)
        statusText.text = "Service: ${if (running) "RUNNING" else "STOPPED"}\nMarket: ${if (Market.isOpen()) "OPEN" else "CLOSED"}\nStatus: ${Store.status(this)}\nLast check: ${Store.lastCheck(this)} IST"
        statusText.setTextColor(if (running) Color.rgb(103, 232, 170) else Color.rgb(251, 146, 146))
        startStopButton.background = bg(if (running) Color.rgb(190, 63, 82) else blue, 14)
        startStopButton.text = if (running) "Stop monitoring" else "Start monitoring"
        refreshZones()
        logsText.text = Store.logs(this).joinToString("\n").ifEmpty { "No logs yet" }
    }
    private fun refreshZones() {
        zoneContainer.removeAllViews()
        val zones = Store.zones(this)
        if (zones.isEmpty()) { add(zoneContainer, text("No zones yet. Add your first alert range above.", 13f).apply { setTextColor(muted) }); return }
        for (zone in zones) {
            val card = column().apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = bg(surface, 18, Color.rgb(36, 54, 83))
            }
            val state = if (zone.enabled) "ACTIVE" else "PAUSED"
            val range = String.format(Locale.US, "%,.2f - %,.2f", zone.lower, zone.upper)
            add(card, text("${zone.index}  •  $state", 13f).apply { setTextColor(if (zone.enabled) Color.rgb(103, 232, 170) else muted) })
            add(card, text(range, 21f).apply { setTypeface(null, Typeface.BOLD) })
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            controls.addView(button(if (zone.enabled) "Pause" else "Resume") {
                Store.toggleZone(this, zone.id)
                refreshUi()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            controls.addView(button("Delete") {
                Store.deleteZone(this, zone.id)
                refreshUi()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            add(card, controls)
            zoneContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }
    private fun startMonitoring() {
        val i = Intent(this, MonitorService::class.java).apply { action = MonitorService.ACTION_START }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            Store.setRunning(this, true)
            Store.setStatus(this, "Starting")
            toast("Monitoring start requested")
        } catch (e: Exception) {
            Store.setRunning(this, false)
            Store.setStatus(this, "Start failed")
            Store.log(this, "Service start error: ${e.message}")
            toast("Could not start monitoring")
        }
        refreshUi()
    }
    private fun stopMonitoring() {
        Store.setRunning(this, false)
        Store.setStatus(this, "Stopped")
        val i = Intent(this, MonitorService::class.java).apply { action = MonitorService.ACTION_STOP }
        try { startService(i) }
        catch (e: Exception) {
            Store.log(this, "Stop command error: ${e.message}")
            stopService(Intent(this, MonitorService::class.java))
        }
        refreshUi()
    }
    private fun requestBatteryExemption() {
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        if (manager.isIgnoringBatteryOptimizations(packageName)) {
            toast("Battery optimization exemption already enabled")
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (_: Exception) { toast("Open battery settings manually") }
        }
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) toast(if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            "Notifications enabled" else "Enable notifications in Android settings")
    }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}
