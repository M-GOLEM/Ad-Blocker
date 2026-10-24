package com.adblocker.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adblocker.app.data.BlockStats
import com.adblocker.app.data.DomainStore
import com.adblocker.app.data.FilterCatalog
import com.adblocker.app.data.ListLoader
import com.adblocker.app.service.AdBlockerVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences(FilterCatalog.PREFS, Context.MODE_PRIVATE)

    private val _selected = MutableStateFlow(FilterCatalog.selectedIds(ctx))
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _dark = MutableStateFlow(prefs.getBoolean(FilterCatalog.KEY_DARK, true))
    val darkMode: StateFlow<Boolean> = _dark.asStateFlow()

    private val _autoStart = MutableStateFlow(prefs.getBoolean(FilterCatalog.KEY_AUTO, false))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val running = BlockStats.running
    val preparing = BlockStats.preparing
    val status = BlockStats.status
    val blocked = BlockStats.blocked
    val allowed = BlockStats.allowed
    val recent = BlockStats.recent
    val domainCount = DomainStore.size

    /** Select the protection level (Basic / Balanced / Strong). */
    fun setLevel(ids: Set<String>) {
        _selected.value = ids
        prefs.edit().putStringSet(FilterCatalog.KEY_LISTS, ids).apply()
    }

    fun setDarkMode(enabled: Boolean) {
        _dark.value = enabled
        prefs.edit().putBoolean(FilterCatalog.KEY_DARK, enabled).apply()
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        prefs.edit().putBoolean(FilterCatalog.KEY_AUTO, enabled).apply()
    }

    fun refreshLists() {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val chosen = FilterCatalog.all.filter { it.id in _selected.value }
            ListLoader.clearCache(ctx)
            val count = ListLoader.download(chosen) { }
            BlockStats.setStatus(
                if (count > 0) "Protection updated successfully" else "Update failed. Check your internet connection."
            )
            _busy.value = false
        }
    }

    fun start() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, AdBlockerVpnService::class.java))
    }

    fun stop() {
        ctx.startService(
            Intent(ctx, AdBlockerVpnService::class.java).setAction(AdBlockerVpnService.ACTION_STOP)
        )
    }

    fun resetStats() {
        BlockStats.reset()
    }
}
