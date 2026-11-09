package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class TrafficShaperVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnInputStream: FileInputStream? = null
    private var vpnOutputStream: FileOutputStream? = null
    private var vpnThread: Thread? = null
    @Volatile
    private var isRunning = false
    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val TAG = "AppDataBlockerVpn"
        private const val CHANNEL_ID = "AppDataBlockerVpnChannel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.START"
        const val ACTION_STOP = "com.example.STOP"
        const val ACTION_REFRESH = "com.example.REFRESH"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand with action: $action")

        if (action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked by system or another VPN connected")
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    private fun startVpn() {
        settingsManager.isVpnActive = true

        createNotificationChannel()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!isRunning) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
            Log.d(TAG, "VPN already running, refreshed notification and resetting interface...")
            refreshVpnInterface()
            return
        }

        isRunning = true
        vpnThread = Thread({ runVpnLoop() }, "AppDataBlockerVpnThread").apply { start() }
    }

    private fun stopVpn() {
        isRunning = false
        settingsManager.isVpnActive = false

        closeStreamsAndInterface()

        vpnThread?.interrupt()
        vpnThread = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d(TAG, "VPN stopped completely")
    }

    private fun refreshVpnInterface() {
        closeStreamsAndInterface()
    }

    private fun closeStreamsAndInterface() {
        try {
            vpnInputStream?.close()
        } catch (e: Exception) {
            Log.v(TAG, "Error closing VPN input stream", e)
        }
        vpnInputStream = null

        try {
            vpnOutputStream?.close()
        } catch (e: Exception) {
            Log.v(TAG, "Error closing VPN output stream", e)
        }
        vpnOutputStream = null

        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.v(TAG, "Error closing VPN interface descriptor", e)
        }
        vpnInterface = null
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TrafficShaperVpnService::class.java).apply {
            action = ACTION_STOP
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flag)

        val shieldsText = buildString {
            if (settingsManager.isSystemShieldEnabled) append("System ")
            if (settingsManager.isInstalledShieldEnabled) append("Installed ")
            if (!settingsManager.isSystemShieldEnabled && !settingsManager.isInstalledShieldEnabled) append("No Shields Active")
            else append("Shield Active")
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, flag)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppData Blocker Active")
            .setContentText(shieldsText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Deactivate Protection",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AppData Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows AppData Blocker firewall active status"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun runVpnLoop() {
        val buffer = ByteBuffer.allocate(32768)
        var consecutiveEstablishFailures = 0

        while (isRunning) {
            try {
                if (vpnInterface == null) {
                    establishVpn()
                    if (vpnInterface == null) {
                        consecutiveEstablishFailures++
                        Log.w(TAG, "VPN establish returned null (failure #$consecutiveEstablishFailures)")
                        if (consecutiveEstablishFailures >= 3) {
                            Log.e(TAG, "Failed to establish VPN interface 3 times. Stopping service.")
                            stopVpn()
                            stopSelf()
                            break
                        }
                        Thread.sleep(1000)
                        continue
                    }
                    consecutiveEstablishFailures = 0
                    vpnInputStream = FileInputStream(vpnInterface?.fileDescriptor)
                    vpnOutputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                }

                val input = vpnInputStream ?: continue

                // Read packets from TUN interface for blocked apps and drop them
                while (isRunning && vpnInterface != null) {
                    val readBytes = try {
                        input.read(buffer.array())
                    } catch (e: IOException) {
                        // Interface was closed or refreshed
                        break
                    }

                    if (readBytes > 0) {
                        // FIREWALL BLACKHOLE:
                        // Discard all packets from blocked apps.
                        // We strictly DO NOT forward UDP/DNS/QUIC or TCP packets out to the real network,
                        // preventing any unauthorized data exfiltration or firewall bypass.
                        buffer.clear()
                    } else if (readBytes < 0) {
                        break
                    } else {
                        Thread.sleep(25)
                    }
                }
            } catch (e: InterruptedException) {
                Log.i(TAG, "VPN loop interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Exception in VPN loop, resetting interface...", e)
                closeStreamsAndInterface()
                try {
                    Thread.sleep(1000)
                } catch (ie: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun establishVpn() {
        val builder = Builder()

        // Configure standard IP routing for IPv4
        builder.setMtu(1500)
        builder.addAddress("10.0.0.2", 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")

        // Configure IPv6 routing to prevent IPv6 bypass leaks
        try {
            builder.addAddress("fd00:1::2", 128)
            builder.addRoute("::", 0)
            builder.addDnsServer("2001:4860:4860::8888")
        } catch (e: Exception) {
            Log.w(TAG, "IPv6 address/route configuration skipped: ${e.message}")
        }

        builder.setSession("AppDataBlockerLocalTunnel")

        // Populate apps to route into VPN based on active shields
        val blockedApps = getBlockedAppsList()

        if (blockedApps.isEmpty()) {
            // When no apps are blocked, route an installed offline package or stub.
            // Never pick an app with internet access or AppData Blocker itself!
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val offlineDummy = packages.firstOrNull { pkg ->
                val perms = pkg.requestedPermissions
                pkg.packageName != packageName &&
                (pkg.packageName.startsWith("com.android.egg") ||
                 pkg.packageName == "com.android.shell" ||
                 perms == null ||
                 !perms.contains(android.Manifest.permission.INTERNET))
            }

            if (offlineDummy != null) {
                try {
                    builder.addAllowedApplication(offlineDummy.packageName)
                    Log.d(TAG, "Routing harmless offline dummy target to keep VPN alive: ${offlineDummy.packageName}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding dummy target", e)
                }
            }
        } else {
            for (appPkg in blockedApps) {
                try {
                    builder.addAllowedApplication(appPkg)
                    Log.d(TAG, "Routing app to VPN to block: $appPkg")
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Package not found to route: $appPkg", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding package: $appPkg", e)
                }
            }
        }

        vpnInterface = builder.establish()
        Log.i(TAG, "VPN Interface established with ${blockedApps.size} blocked applications")
    }

    /**
     * Identifies which packages should have their internet traffic blocked (routed into VPN).
     */
    private fun getBlockedAppsList(): List<String> {
        val blockedList = mutableListOf<String>()
        val pm = packageManager

        val isSystemShield = settingsManager.isSystemShieldEnabled
        val isInstalledShield = settingsManager.isInstalledShieldEnabled
        val exempted = settingsManager.exemptApps

        val packages = pm.getInstalledPackages(0)
        for (pkgInfo in packages) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            val pkgName = pkgInfo.packageName ?: continue

            // Never block ourselves
            if (pkgName == packageName) continue

            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isCoreSystem = appInfo.uid < 10000 || SettingsManager.isEssentialSystemPackage(pkgName)

            if (exempted.contains(pkgName)) {
                continue // User exempted this app
            }

            if (isSystem) {
                if (isSystemShield && !isCoreSystem) {
                    blockedList.add(pkgName)
                }
            } else {
                if (isInstalledShield) {
                    blockedList.add(pkgName)
                }
            }
        }

        return blockedList
    }
}
