package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataBlockerUnitTest {

    @Test
    fun testEssentialSystemPackagesClassification() {
        assertTrue(SettingsManager.isEssentialSystemPackage("android"))
        assertTrue(SettingsManager.isEssentialSystemPackage("com.android.systemui"))
        assertTrue(SettingsManager.isEssentialSystemPackage("com.google.android.gms"))
        assertTrue(SettingsManager.isEssentialSystemPackage("com.android.vending"))
        assertTrue(SettingsManager.isEssentialSystemPackage("com.google.android.networkstack"))
        assertTrue(SettingsManager.isEssentialSystemPackage("com.android.providers.settings"))

        // Regular 3rd party apps or non-essential apps
        assertFalse(SettingsManager.isEssentialSystemPackage("com.example.game"))
        assertFalse(SettingsManager.isEssentialSystemPackage("com.social.network"))
        assertFalse(SettingsManager.isEssentialSystemPackage("org.mozilla.firefox"))
    }

    @Test
    fun testFormatTrafficSpeed() {
        assertEquals("0 B/s", formatTrafficSpeed(0L))
        assertEquals("512 B/s", formatTrafficSpeed(512L))
        assertEquals("1 KB/s", formatTrafficSpeed(1024L))
        assertEquals("500 KB/s", formatTrafficSpeed(500 * 1024L))
        assertEquals("1.0 MB/s", formatTrafficSpeed(1024 * 1024L))
        assertEquals("5.5 MB/s", formatTrafficSpeed((5.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testExemptionQuotaEnforcement() {
        val exemptSet = mutableSetOf<String>()
        val maxQuota = SettingsManager.MAX_EXEMPT_APPS
        assertEquals(11, maxQuota)

        for (i in 1..maxQuota) {
            val canAdd = exemptSet.size < maxQuota
            assertTrue("Should allow adding up to $maxQuota apps", canAdd)
            exemptSet.add("com.example.app$i")
        }
        assertEquals(11, exemptSet.size)

        // Adding 12th
        val canAdd12 = exemptSet.size < maxQuota
        assertFalse("Cannot exceed quota of 11 apps", canAdd12)

        // After removing one
        exemptSet.remove("com.example.app1")
        assertEquals(10, exemptSet.size)
        val canAddAfterRemoval = exemptSet.size < maxQuota
        assertTrue("Can add after removal", canAddAfterRemoval)
        exemptSet.add("com.example.app12")
        assertEquals(11, exemptSet.size)
    }

    @Test
    fun testBlockLogicRules() {
        // App exempted: never blocked
        val isExempted = true
        val sysShield = true
        val instShield = true
        val isSystem = false
        val uid = 10050
        val pkg = "com.test.app"

        val isBlockedExempted = if (isExempted) {
            false
        } else if (isSystem) {
            sysShield && uid >= 10000 && !SettingsManager.isEssentialSystemPackage(pkg)
        } else {
            instShield
        }
        assertFalse("Exempted app must never be blocked", isBlockedExempted)

        // User installed app with installed shield enabled
        val isBlockedInstalled = if (false) {
            false
        } else if (false) {
            false
        } else {
            instShield
        }
        assertTrue("Installed app must be blocked when installed shield is active", isBlockedInstalled)

        // Core system app must bypass system shield
        val corePkg = "com.android.systemui"
        val isBlockedCore = sysShield && 10001 >= 10000 && !SettingsManager.isEssentialSystemPackage(corePkg)
        assertFalse("Core system package must bypass firewall", isBlockedCore)

        // Non-core system app with system shield enabled
        val nonCoreSysPkg = "com.vendor.bloatware"
        val isBlockedNonCore = sysShield && 10050 >= 10000 && !SettingsManager.isEssentialSystemPackage(nonCoreSysPkg)
        assertTrue("Non-essential system app must be blocked by system shield", isBlockedNonCore)
    }
}
