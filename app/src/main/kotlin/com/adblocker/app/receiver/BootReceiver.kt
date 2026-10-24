package com.adblocker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.adblocker.app.data.FilterCatalog
import com.adblocker.app.service.AdBlockerVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(FilterCatalog.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(FilterCatalog.KEY_AUTO, false)) return
        // VPN permission must already have been granted from the app.
        if (VpnService.prepare(context) != null) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, AdBlockerVpnService::class.java))
        } catch (e: Exception) {
            // The system may block starting services from the background; ignore.
        }
    }
}
