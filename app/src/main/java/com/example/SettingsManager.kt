package com.example

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "netshield_settings"
        const val KEY_VPN_ACTIVE = "vpn_active"
        const val KEY_SYSTEM_SHIELD = "system_shield_enabled"
        const val KEY_INSTALLED_SHIELD = "installed_shield_enabled"
        const val KEY_EXEMPT_APPS = "exempt_apps"
        const val KEY_WELCOME_CARD_DISMISSED = "welcome_card_dismissed"
        const val MAX_EXEMPT_APPS = 11

        private val ESSENTIAL_PREFIXES = listOf(
            "android",
            "com.android.providers.settings",
            "com.android.systemui",
            "com.android.vending",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.inputmethod",
            "com.android.shell",
            "com.android.networkstack",
            "com.google.android.networkstack",
            "com.android.captiveportallogin"
        )

        fun isEssentialSystemPackage(pkgName: String): Boolean {
            return ESSENTIAL_PREFIXES.any { pkgName == it || pkgName.startsWith("$it.") }
        }
    }

    var isVpnActive: Boolean
        get() = prefs.getBoolean(KEY_VPN_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_ACTIVE, value).apply()

    var isSystemShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_SHIELD, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_SHIELD, value).apply()

    var isInstalledShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_INSTALLED_SHIELD, false)
        set(value) = prefs.edit().putBoolean(KEY_INSTALLED_SHIELD, value).apply()

    var isWelcomeCardDismissed: Boolean
        get() = prefs.getBoolean(KEY_WELCOME_CARD_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME_CARD_DISMISSED, value).apply()

    var exemptApps: Set<String>
        get() = prefs.getStringSet(KEY_EXEMPT_APPS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXEMPT_APPS, HashSet(value)).apply()

    fun addExemptApp(packageName: String): Boolean {
        val current = exemptApps.toMutableSet()
        if (current.size >= MAX_EXEMPT_APPS) return false
        current.add(packageName)
        exemptApps = current
        return true
    }

    fun removeExemptApp(packageName: String) {
        val current = exemptApps.toMutableSet()
        current.remove(packageName)
        exemptApps = current
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
