package com.zonealerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !Store.isRunning(context)) return
        val i = Intent(context, MonitorService::class.java).apply { action = MonitorService.ACTION_START }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            Store.log(context, "Boot completed: monitoring restart requested")
        } catch (e: Exception) {
            Store.log(context, "Boot restart failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
