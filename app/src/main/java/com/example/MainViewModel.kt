package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val settingsManager = SettingsManager(context)
    private val pm = context.packageManager

    // UI States
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _isSystemShieldEnabled = MutableStateFlow(false)
    val isSystemShieldEnabled: StateFlow<Boolean> = _isSystemShieldEnabled.asStateFlow()

    private val _isInstalledShieldEnabled = MutableStateFlow(false)
    val isInstalledShieldEnabled: StateFlow<Boolean> = _isInstalledShieldEnabled.asStateFlow()

    private val _exemptPackages = MutableStateFlow<Set<String>>(emptySet())
    val exemptPackages: StateFlow<Set<String>> = _exemptPackages.asStateFlow()

    private val _isWelcomeCardDismissed = MutableStateFlow(false)
    val isWelcomeCardDismissed: StateFlow<Boolean> = _isWelcomeCardDismissed.asStateFlow()

    private val _appsList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appsList: StateFlow<List<AppInfo>> = _appsList.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _realtimeDownloadSpeed = MutableStateFlow(0L)
    val realtimeDownloadSpeed: StateFlow<Long> = _realtimeDownloadSpeed.asStateFlow()

    private val _realtimeUploadSpeed = MutableStateFlow(0L)
    val realtimeUploadSpeed: StateFlow<Long> = _realtimeUploadSpeed.asStateFlow()

    // Event flow for user messages (e.g. limit reached)
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private var speedMonitorJob: Job? = null

    // Preference change listener to keep UI in sync if changed by Service or external events
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SettingsManager.KEY_VPN_ACTIVE -> {
                val active = settingsManager.isVpnActive
                if (_isVpnActive.value != active) {
                    _isVpnActive.value = active
                    if (active) startSpeedMonitor() else stopSpeedMonitor()
                    refreshAppsList()
                }
            }
            SettingsManager.KEY_SYSTEM_SHIELD -> {
                _isSystemShieldEnabled.value = settingsManager.isSystemShieldEnabled
                refreshAppsList()
            }
            SettingsManager.KEY_INSTALLED_SHIELD -> {
                _isInstalledShieldEnabled.value = settingsManager.isInstalledShieldEnabled
                refreshAppsList()
            }
            SettingsManager.KEY_EXEMPT_APPS -> {
                _exemptPackages.value = settingsManager.exemptApps
                refreshAppsList()
            }
        }
    }

    init {
        // Load initial state
        _isVpnActive.value = settingsManager.isVpnActive
        _isSystemShieldEnabled.value = settingsManager.isSystemShieldEnabled
        _isInstalledShieldEnabled.value = settingsManager.isInstalledShieldEnabled
        _exemptPackages.value = settingsManager.exemptApps
        _isWelcomeCardDismissed.value = settingsManager.isWelcomeCardDismissed

        settingsManager.registerListener(prefListener)

        if (settingsManager.isVpnActive) {
            startSpeedMonitor()
        }

        refreshAppsList()
    }

    override fun onCleared() {
        settingsManager.unregisterListener(prefListener)
        stopSpeedMonitor()
        super.onCleared()
    }

    /**
     * Refreshes the package list, categorizes into System/User, and checks block status
     */
    fun refreshAppsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val packages = pm.getInstalledPackages(0)
                val exempts = _exemptPackages.value
                val sysShield = _isSystemShieldEnabled.value
                val instShield = _isInstalledShieldEnabled.value

                val appInfos = packages.mapNotNull { pkgInfo ->
                    val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                    val pkgName = pkgInfo.packageName ?: return@mapNotNull null

                    // Skip our own app
                    if (pkgName == context.packageName) return@mapNotNull null

                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isExempted = exempts.contains(pkgName)
                    val uid = appInfo.uid

                    // Block evaluation:
                    // If exempted, never blocked
                    // Else, block based on active shields & core system bypass
                    val isBlocked = if (isExempted) {
                        false
                    } else if (isSystem) {
                        sysShield && uid >= 10000 && !SettingsManager.isEssentialSystemPackage(pkgName)
                    } else {
                        instShield
                    }

                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    AppInfo(
                        packageName = pkgName,
                        appName = appName,
                        icon = icon,
                        isSystem = isSystem,
                        isExempted = isExempted,
                        isBlocked = isBlocked,
                        uid = uid
                    )
                }.sortedBy { it.appName.lowercase() }

                _appsList.value = appInfos
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error fetching packages", e)
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    // Settings modifiers
    fun toggleVpnState(active: Boolean) {
        if (active && !_isSystemShieldEnabled.value && !_isInstalledShieldEnabled.value) {
            // Auto-enable installed shield so protection is genuinely active
            _isInstalledShieldEnabled.value = true
            settingsManager.isInstalledShieldEnabled = true
        }

        _isVpnActive.value = active
        settingsManager.isVpnActive = active

        val intent = Intent(context, TrafficShaperVpnService::class.java).apply {
            action = if (active) TrafficShaperVpnService.ACTION_START else TrafficShaperVpnService.ACTION_STOP
        }

        try {
            if (active) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                startSpeedMonitor()
            } else {
                context.startService(intent)
                stopSpeedMonitor()
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to transition VPN state", e)
            // Roll back state if start failed
            _isVpnActive.value = false
            settingsManager.isVpnActive = false
            stopSpeedMonitor()
        }

        refreshAppsList()
    }

    fun toggleSystemShield() {
        val newVal = !_isSystemShieldEnabled.value
        _isSystemShieldEnabled.value = newVal
        settingsManager.isSystemShieldEnabled = newVal
        triggerVpnRefresh()
        refreshAppsList()
    }

    fun toggleInstalledShield() {
        val newVal = !_isInstalledShieldEnabled.value
        _isInstalledShieldEnabled.value = newVal
        settingsManager.isInstalledShieldEnabled = newVal
        triggerVpnRefresh()
        refreshAppsList()
    }

    fun toggleAppExemption(packageName: String) {
        val current = _exemptPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
            settingsManager.removeExemptApp(packageName)
        } else {
            if (current.size >= SettingsManager.MAX_EXEMPT_APPS) {
                viewModelScope.launch {
                    _userMessage.emit("Exemption limit reached (11 apps). Remove an existing app first.")
                }
                return
            }
            current.add(packageName)
            settingsManager.addExemptApp(packageName)
        }
        _exemptPackages.value = current
        triggerVpnRefresh()
        refreshAppsList()
    }

    fun dismissWelcomeCard() {
        _isWelcomeCardDismissed.value = true
        settingsManager.isWelcomeCardDismissed = true
    }

    fun resetWelcomeCard() {
        _isWelcomeCardDismissed.value = false
        settingsManager.isWelcomeCardDismissed = false
    }

    private fun triggerVpnRefresh() {
        if (_isVpnActive.value) {
            val intent = Intent(context, TrafficShaperVpnService::class.java).apply {
                action = TrafficShaperVpnService.ACTION_REFRESH
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to refresh VPN service", e)
            }
        }
    }

    private fun startSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = viewModelScope.launch(Dispatchers.IO) {
            var lastRxBytes = android.net.TrafficStats.getTotalRxBytes()
            var lastTxBytes = android.net.TrafficStats.getTotalTxBytes()
            var lastTime = System.currentTimeMillis()

            if (lastRxBytes == android.net.TrafficStats.UNSUPPORTED.toLong()) {
                lastRxBytes = 0L
            }
            if (lastTxBytes == android.net.TrafficStats.UNSUPPORTED.toLong()) {
                lastTxBytes = 0L
            }

            while (isActive) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val currentRxBytes = android.net.TrafficStats.getTotalRxBytes()
                val currentTxBytes = android.net.TrafficStats.getTotalTxBytes()

                val timeDiffSec = (currentTime - lastTime) / 1000.0
                if (timeDiffSec > 0 && currentRxBytes != android.net.TrafficStats.UNSUPPORTED.toLong() && currentTxBytes != android.net.TrafficStats.UNSUPPORTED.toLong()) {
                    val rxDiff = if (currentRxBytes >= lastRxBytes) currentRxBytes - lastRxBytes else 0L
                    val txDiff = if (currentTxBytes >= lastTxBytes) currentTxBytes - lastTxBytes else 0L

                    _realtimeDownloadSpeed.value = (rxDiff / timeDiffSec).toLong()
                    _realtimeUploadSpeed.value = (txDiff / timeDiffSec).toLong()

                    lastRxBytes = currentRxBytes
                    lastTxBytes = currentTxBytes
                }
                lastTime = currentTime
            }
        }
    }

    private fun stopSpeedMonitor() {
        speedMonitorJob?.cancel()
        speedMonitorJob = null
        _realtimeDownloadSpeed.value = 0L
        _realtimeUploadSpeed.value = 0L
    }
}
