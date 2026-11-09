package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.material.icons.automirrored.filled.*

// Premium Immersive UI Color Palette
val CyberDarkBg = Color(0xFF0F1113) // Immersive background
val CyberCardBg = Color(0xFF1D2024) // Immersive card background
val CyberCyan = Color(0xFFD0E4FF) // Immersive soft blue accent
val CyberGreen = Color(0xFF40FF00) // Immersive green
val CyberRed = Color(0xFFFF3D00)
val CyberOrange = Color(0xFFFF9100)
val CyberTextSecondary = Color(0xFF909094) // Immersive secondary text
val CyberTextDisabled = Color(0xFF45494E) // Immersive disabled text
val ImmersiveBorder = Color(0xFF2D3135) // Immersive borders
val ImmersiveActiveBlue = Color(0xFF004A77) // Immersive active button/accent background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val isSystemShieldEnabled by viewModel.isSystemShieldEnabled.collectAsState()
    val isInstalledShieldEnabled by viewModel.isInstalledShieldEnabled.collectAsState()
    val exemptPackages by viewModel.exemptPackages.collectAsState()
    val appsList by viewModel.appsList.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    val realtimeDownloadSpeed by viewModel.realtimeDownloadSpeed.collectAsState()
    val realtimeUploadSpeed by viewModel.realtimeUploadSpeed.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = User Apps, 1 = System Apps
    var showExemptionRosterDialog by remember { mutableStateOf(false) }
    var showAboutDeveloperDialog by remember { mutableStateOf(false) }
    var showHowToUseDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    val isWelcomeCardDismissed by viewModel.isWelcomeCardDismissed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect user messages from ViewModel (such as exemption limit reached)
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Runtime permission launcher for notifications on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* User granted or denied notification permission */ }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Launcher for VPN authorization intent
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                viewModel.toggleVpnState(true)
            }
        }
    )

    fun handleVpnToggle(active: Boolean) {
        if (active) {
            try {
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent != null) {
                    try {
                        vpnPrepareLauncher.launch(vpnIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        viewModel.toggleVpnState(true)
                    }
                } else {
                    viewModel.toggleVpnState(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.toggleVpnState(true)
            }
        } else {
            viewModel.toggleVpnState(false)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Shield Logo",
                            tint = CyberCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "APPDATA BLOCKER",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHowToUseDialog = true },
                        modifier = Modifier.testTag("top_bar_help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "How to Use Guide",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        modifier = Modifier.testTag("top_bar_privacy_policy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = "Privacy Policy",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAboutDeveloperDialog = true },
                        modifier = Modifier.testTag("top_bar_about_dev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "About Developer & Support",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (isVpnActive) "PROTECTED" else "UNPROTECTED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isVpnActive) CyberGreen else CyberRed,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = isVpnActive,
                            onCheckedChange = { handleVpnToggle(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberGreen,
                                checkedTrackColor = CyberGreen.copy(alpha = 0.3f),
                                uncheckedThumbColor = CyberRed,
                                uncheckedTrackColor = CyberRed.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("global_vpn_switch")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDarkBg,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = CyberDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CyberDarkBg)
        ) {
            if (isVpnActive) {
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, CyberGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CyberGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE DATA SPEED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Download",
                                tint = CyberGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatTrafficSpeed(realtimeDownloadSpeed),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Upload",
                                tint = CyberCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatTrafficSpeed(realtimeUploadSpeed),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // VPN Status Card
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ProtectionDashboardCard(
                        isVpnActive = isVpnActive,
                        isSystemShieldEnabled = isSystemShieldEnabled,
                        isInstalledShieldEnabled = isInstalledShieldEnabled,
                        realtimeDlSpeed = realtimeDownloadSpeed,
                        realtimeUlSpeed = realtimeUploadSpeed
                    )
                }

                // First-Time Welcome Card (Dismissible)
                item {
                    AnimatedVisibility(
                        visible = !isWelcomeCardDismissed,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        FirstTimeWelcomeCard(
                            onViewGuide = { showHowToUseDialog = true },
                            onDismiss = { viewModel.dismissWelcomeCard() }
                        )
                    }
                }

                // Dual Shields Firewall Control
                item {
                    DualShieldsSection(
                        isSystemShieldEnabled = isSystemShieldEnabled,
                        isInstalledShieldEnabled = isInstalledShieldEnabled,
                        exemptCount = exemptPackages.size,
                        onToggleSystemShield = { viewModel.toggleSystemShield() },
                        onToggleInstalledShield = { viewModel.toggleInstalledShield() }
                    )
                }

                // Whitelisted Apps Overview & Explanatory Tag
                item {
                    AppWhitelistCounterCard(
                        exemptCount = exemptPackages.size,
                        onClick = { showExemptionRosterDialog = true }
                    )
                }

                // How to Use Guide Dashboard Card
                item {
                    HowToUseDashboardCard(
                        onClick = { showHowToUseDialog = true }
                    )
                }

                // About Developer & Support Banner
                item {
                    DeveloperSupportBannerCard(
                        onClick = { showAboutDeveloperDialog = true }
                    )
                }

                // Apps List Tabs & Search Bar
                item {
                    Text(
                        text = "APPLICATION RULES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    
                    SearchBarField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    AppTypeTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        userAppsCount = appsList.count { !it.isSystem },
                        systemAppsCount = appsList.count { it.isSystem }
                    )
                }

                // List of filtered apps
                val filteredApps = appsList.filter { app ->
                    val matchesTab = if (selectedTab == 0) !app.isSystem else app.isSystem
                    val matchesSearch = app.appName.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                    matchesTab && matchesSearch
                }

                if (isLoadingApps) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyberCyan)
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = "No results",
                                    tint = CyberTextSecondary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No applications found",
                                    color = CyberTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { appInfo ->
                        AppListItemRow(
                            appInfo = appInfo,
                            exemptCount = exemptPackages.size,
                            onExemptToggled = { viewModel.toggleAppExemption(appInfo.packageName) }
                        )
                    }
                }

                // Bottom padding inside list
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    ExemptionRosterDialog(
        isOpen = showExemptionRosterDialog,
        onDismiss = { showExemptionRosterDialog = false },
        appsList = appsList,
        exemptPackages = exemptPackages,
        onToggleExemption = { viewModel.toggleAppExemption(it) }
    )

    AboutDeveloperDialog(
        isOpen = showAboutDeveloperDialog,
        onDismiss = { showAboutDeveloperDialog = false },
        onOpenPrivacyPolicy = { showPrivacyPolicyDialog = true }
    )

    HowToUseDialog(
        isOpen = showHowToUseDialog,
        onDismiss = { showHowToUseDialog = false }
    )

    PrivacyPolicyDialog(
        isOpen = showPrivacyPolicyDialog,
        onDismiss = { showPrivacyPolicyDialog = false }
    )
}

fun formatTrafficSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

@Composable
fun ProtectionDashboardCard(
    isVpnActive: Boolean,
    isSystemShieldEnabled: Boolean,
    isInstalledShieldEnabled: Boolean,
    realtimeDlSpeed: Long,
    realtimeUlSpeed: Long
) {
    val neitherShieldActive = isVpnActive && !isSystemShieldEnabled && !isInstalledShieldEnabled
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (neitherShieldActive) CyberOrange.copy(alpha = 0.5f)
                else if (isVpnActive) CyberCyan.copy(alpha = 0.3f)
                else ImmersiveBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (neitherShieldActive) CyberOrange.copy(alpha = 0.15f)
                        else if (isVpnActive) CyberGreen.copy(alpha = 0.15f)
                        else CyberRed.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (neitherShieldActive) Icons.Filled.WarningAmber else if (isVpnActive) Icons.Filled.Shield else Icons.Filled.ShieldMoon,
                    contentDescription = "Protection Status",
                    tint = if (neitherShieldActive) CyberOrange else if (isVpnActive) CyberGreen else CyberRed,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (neitherShieldActive) "FIREWALL ACTIVE (NO SHIELDS)" else if (isVpnActive) "FIREWALL ACTIVE" else "FIREWALL DISABLED",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (neitherShieldActive) CyberOrange else if (isVpnActive) CyberGreen else CyberRed,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (neitherShieldActive) "No apps are currently blocked. Turn on System or Installed Shield below."
                       else if (isVpnActive) "All unauthorized background traffic is blocked"
                       else "Turn on VPN protection to enforce rules",
                fontSize = 11.sp,
                color = if (neitherShieldActive) CyberOrange else CyberTextSecondary,
                textAlign = TextAlign.Center
            )

            if (isVpnActive) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = ImmersiveBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Download Traffic",
                                tint = CyberGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DOWNLOAD",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatTrafficSpeed(realtimeDlSpeed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(ImmersiveBorder)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Upload Traffic",
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPLOAD",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatTrafficSpeed(realtimeUlSpeed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DualShieldsSection(
    isSystemShieldEnabled: Boolean,
    isInstalledShieldEnabled: Boolean,
    exemptCount: Int,
    onToggleSystemShield: () -> Unit,
    onToggleInstalledShield: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "DUAL-SHIELD FIREWALL",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
            color = CyberCyan,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // System Apps Shield Card
            ShieldCard(
                title = "System Apps Shield",
                icon = Icons.Filled.Dns,
                isEnabled = isSystemShieldEnabled,
                accentColor = CyberRed,
                onToggle = onToggleSystemShield,
                statusText = if (isSystemShieldEnabled) "LOCKDOWN ON" else "ALLOW ALL",
                modifier = Modifier
                    .weight(1f)
                    .testTag("system_shield_card")
            )

            // Installed Apps Shield Card
            ShieldCard(
                title = "Installed Apps Shield",
                icon = Icons.Filled.AppSettingsAlt,
                isEnabled = isInstalledShieldEnabled,
                accentColor = CyberOrange,
                onToggle = onToggleInstalledShield,
                statusText = if (isInstalledShieldEnabled) "LOCKDOWN ON" else "ALLOW ALL",
                modifier = Modifier
                    .weight(1f)
                    .testTag("installed_shield_card")
            )
        }
    }
}

@Composable
fun ShieldCard(
    title: String,
    icon: ImageVector,
    isEnabled: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    statusText: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = modifier
            .border(
                BorderStroke(
                    1.dp,
                    if (isEnabled) CyberCyan.copy(alpha = 0.4f) else ImmersiveBorder
                ),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isEnabled) ImmersiveActiveBlue.copy(alpha = 0.3f) else CyberDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isEnabled) CyberCyan else CyberTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = statusText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) accentColor else CyberTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Switch representation
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentColor,
                    checkedTrackColor = accentColor.copy(alpha = 0.3f),
                    uncheckedThumbColor = CyberTextSecondary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.05f)
                ),
                modifier = Modifier.scale(0.85f)
            )
        }
    }
}

// Simple extension helper to scale switches or components slightly
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            (placeable.width * scale).toInt(),
            (placeable.height * scale).toInt()
        ) {
            placeable.placeRelativeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
)

@Composable
fun AppWhitelistCounterCard(exemptCount: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, ImmersiveBorder), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ImmersiveActiveBlue.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = "Shield Verified",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EXEMPTION ROSTER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Whitelisted apps bypass the active firewall blocks.",
                    fontSize = 10.sp,
                    color = CyberTextSecondary,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333538))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$exemptCount/11",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (exemptCount >= 11) CyberRed else CyberCyan,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Edit Roster",
                    tint = CyberCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search by name or package...", color = CyberTextSecondary, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = CyberCyan) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = CyberTextSecondary)
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = CyberCardBg,
            unfocusedContainerColor = CyberCardBg,
            focusedBorderColor = CyberCyan.copy(alpha = 0.8f),
            unfocusedBorderColor = ImmersiveBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_search_field")
    )
}

@Composable
fun AppTypeTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    userAppsCount: Int,
    systemAppsCount: Int
) {
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = CyberCyan,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = CyberCyan
            )
        },
        divider = {
            HorizontalDivider(color = ImmersiveBorder)
        }
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "INSTALLED APPS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(
                        containerColor = if (selectedTab == 0) ImmersiveActiveBlue else Color(0xFF333538),
                        contentColor = if (selectedTab == 0) CyberCyan else Color.White
                    ) {
                        Text(text = userAppsCount.toString(), fontSize = 9.sp)
                    }
                }
            },
            modifier = Modifier.testTag("tab_installed_apps")
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SYSTEM APPS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(
                        containerColor = if (selectedTab == 1) ImmersiveActiveBlue else Color(0xFF333538),
                        contentColor = if (selectedTab == 1) CyberCyan else Color.White
                    ) {
                        Text(text = systemAppsCount.toString(), fontSize = 9.sp)
                    }
                }
            },
            modifier = Modifier.testTag("tab_system_apps")
        )
    }
}

@Composable
fun AppListItemRow(
    appInfo: AppInfo,
    exemptCount: Int,
    onExemptToggled: () -> Unit
) {
    val isLimitReached = exemptCount >= 11
    val isOptionDisabled = isLimitReached && !appInfo.isExempted

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (appInfo.isExempted) CyberCyan.copy(alpha = 0.4f)
                    else if (appInfo.isBlocked) CyberRed.copy(alpha = 0.3f)
                    else ImmersiveBorder
                ),
                RoundedCornerShape(16.dp)
            )
            .shadow(if (appInfo.isExempted) 4.shadowDp else 0.shadowDp, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = !isOptionDisabled, onClick = onExemptToggled)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon or safe fallback
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (appInfo.icon != null) {
                    // Load the actual app icon
                    DrawableImage(
                        drawable = appInfo.icon,
                        contentDescription = appInfo.appName,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = "Android App",
                        tint = CyberTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App name and package name details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOptionDisabled) CyberTextDisabled else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = appInfo.packageName,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isOptionDisabled) CyberTextDisabled else CyberTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Block/Whitelisted badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (appInfo.isExempted) {
                        StatusTag(label = "EXEMPTED", color = CyberGreen)
                    } else if (appInfo.isBlocked) {
                        StatusTag(label = "BLOCKED", color = CyberRed)
                    } else {
                        StatusTag(label = "ACTIVE", color = CyberCyan.copy(alpha = 0.6f))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkbox for Exemption Whitelisting
            Checkbox(
                checked = appInfo.isExempted,
                onCheckedChange = { onExemptToggled() },
                enabled = !isOptionDisabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberGreen,
                    uncheckedColor = if (isOptionDisabled) CyberTextDisabled else Color.White.copy(alpha = 0.2f),
                    checkmarkColor = Color.Black,
                    disabledCheckedColor = CyberGreen.copy(alpha = 0.3f),
                    disabledUncheckedColor = CyberTextDisabled.copy(alpha = 0.1f)
                ),
                modifier = Modifier.testTag("app_exempt_checkbox_${appInfo.packageName}")
            )
        }
    }
}

@Composable
fun StatusTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .border(BorderStroke(0.5.dp, color.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

// Simple Shadow representation helper
private val Int.shadowDp get() = this.dp

// Compatibility helper to draw Drawables in Compose safely
@Composable
fun DrawableImage(
    drawable: android.graphics.drawable.Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val androidImageBitmap = remember(drawable) {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 72
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 72
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = androidImageBitmap,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExemptionRosterDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    appsList: List<AppInfo>,
    exemptPackages: Set<String>,
    onToggleExemption: (String) -> Unit
) {
    if (!isOpen) return

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Exempted, 1 = Add More

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(24.dp)),
        containerColor = CyberDarkBg,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveActiveBlue,
                    contentColor = CyberCyan
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("dialog_close_button")
            ) {
                Text(
                    text = "DONE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Shield Roster",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXEMPTION ROSTER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${exemptPackages.size}/11",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (exemptPackages.size >= 11) CyberRed else CyberGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Apps in the roster bypass the firewall block rules completely.",
                    fontSize = 11.sp,
                    color = CyberTextSecondary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search field inside the Dialog
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search apps...",
                            color = CyberTextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = CyberTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CyberCardBg,
                        unfocusedContainerColor = CyberCardBg,
                        focusedBorderColor = CyberCyan.copy(alpha = 0.8f),
                        unfocusedBorderColor = ImmersiveBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("dialog_search_field"),
                    singleLine = true
                )

                // Tab Row for "EXEMPTED" and "ADD MORE"
                val exemptedApps = appsList.filter { it.packageName in exemptPackages }
                val availableApps = appsList.filter { it.packageName !in exemptPackages }

                val filteredExempted = exemptedApps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }
                val filteredAvailable = availableApps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CyberDarkBg,
                    contentColor = CyberCyan,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = CyberCyan
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = ImmersiveBorder)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("dialog_tab_exempted")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "EXEMPTED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selectedTab == 0) Color.White else CyberTextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(
                                containerColor = if (selectedTab == 0) ImmersiveActiveBlue else Color(0xFF333538),
                                contentColor = if (selectedTab == 0) CyberCyan else Color.White
                            ) {
                                Text(text = exemptedApps.size.toString(), fontSize = 9.sp)
                            }
                        }
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("dialog_tab_add_more")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = "ADD MORE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selectedTab == 1) Color.White else CyberTextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge(
                                containerColor = if (selectedTab == 1) ImmersiveActiveBlue else Color(0xFF333538),
                                contentColor = if (selectedTab == 1) CyberCyan else Color.White
                            ) {
                                Text(text = availableApps.size.toString(), fontSize = 9.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List
                val activeList = if (selectedTab == 0) filteredExempted else filteredAvailable

                if (activeList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching apps found"
                                   else if (selectedTab == 0) "No exempted apps yet"
                                   else "All apps are exempted!",
                            color = CyberTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = activeList,
                            key = { it.packageName }
                        ) { app ->
                            RosterAppItem(
                                appInfo = app,
                                isExempted = app.packageName in exemptPackages,
                                isLimitReached = exemptPackages.size >= 11,
                                onToggle = { onToggleExemption(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun RosterAppItem(
    appInfo: AppInfo,
    isExempted: Boolean,
    isLimitReached: Boolean,
    onToggle: () -> Unit
) {
    val isOptionDisabled = isLimitReached && !isExempted

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (isExempted) CyberCyan.copy(alpha = 0.4f) else ImmersiveBorder
                ),
                RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = !isOptionDisabled, onClick = onToggle)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon or safe fallback
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (appInfo.icon != null) {
                    DrawableImage(
                        drawable = appInfo.icon,
                        contentDescription = appInfo.appName,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = "Android App",
                        tint = CyberTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // App name and package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOptionDisabled) CyberTextDisabled else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isOptionDisabled) CyberTextDisabled else CyberTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Checkbox
            Checkbox(
                checked = isExempted,
                onCheckedChange = { onToggle() },
                enabled = !isOptionDisabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = CyberGreen,
                    uncheckedColor = if (isOptionDisabled) CyberTextDisabled else Color.White.copy(alpha = 0.2f),
                    checkmarkColor = Color.Black,
                    disabledCheckedColor = CyberGreen.copy(alpha = 0.3f),
                    disabledUncheckedColor = CyberTextDisabled.copy(alpha = 0.1f)
                ),
                modifier = Modifier.testTag("dialog_app_exempt_checkbox_${appInfo.packageName}")
            )
        }
    }
}

@Composable
fun DeveloperSupportBannerCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("developer_support_banner_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF004A77).copy(alpha = 0.6f),
                                Color(0xFF00796B).copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Support Developer",
                    tint = CyberRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DEEPAK KUMAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyberGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "DEVELOPER",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Independent Developer • Tap for info & UPI support",
                    fontSize = 10.sp,
                    color = CyberTextSecondary,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333538))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SUPPORT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Support",
                    tint = CyberCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDeveloperDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenPrivacyPolicy: (() -> Unit)? = null
) {
    if (!isOpen) return
    val context = LocalContext.current
    var selectedTipAmount by remember { mutableStateOf<Int?>(null) } // null = any amount

    val developerName = "Deepak Kumar"
    val developerRole = "Independent Developer"
    val developerBio = "Dedicated to building privacy-first, on-device Android applications and embedded utilities. Focused on local offline data security and intuitive mobile solutions."
    val developerEmail = "deepakkumar1115@gmail.com"
    val developerGitHub = "https://github.com/deepakkumar1115"
    val upiId = "deepakkumar1115-1@oksbi"

    fun copyToClipboard(label: String, text: String, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun launchUpiPayment() {
        val note = "Support AppData Blocker Development"
        val encodedNote = Uri.encode(note)
        val encodedName = Uri.encode(developerName)
        val amountParam = if (selectedTipAmount != null) "&am=${selectedTipAmount}" else ""
        val upiUriString = "upi://pay?pa=$upiId&pn=$encodedName&cu=INR&tn=$encodedNote$amountParam"

        val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUriString))
        val chooser = Intent.createChooser(upiIntent, "Support Deepak Kumar via UPI")
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            copyToClipboard("UPI ID", upiId, "No UPI app found. UPI ID copied to clipboard!")
        }
    }

    fun launchEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$developerEmail")
                putExtra(Intent.EXTRA_SUBJECT, "[AppData Blocker] Inquiry / Feedback")
            }
            context.startActivity(Intent.createChooser(intent, "Send Email to Developer"))
        } catch (e: Exception) {
            copyToClipboard("Email", developerEmail, "Email copied to clipboard!")
        }
    }

    fun launchGitHub() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(developerGitHub))
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("GitHub URL", developerGitHub, "GitHub URL copied to clipboard!")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(24.dp)),
        containerColor = CyberDarkBg,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveActiveBlue,
                    contentColor = CyberCyan
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("about_dialog_close_button")
            ) {
                Text(
                    text = "CLOSE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Developer",
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ABOUT DEVELOPER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = CyberTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Developer Profile Header
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ImmersiveBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Monogram Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF004A77), Color(0xFF00796B))
                                    )
                                )
                                .border(2.dp, CyberCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "DK",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = developerName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = developerRole.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = developerBio,
                            fontSize = 11.sp,
                            color = CyberTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contact & Links Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ImmersiveBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CONTACT & LINKS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Email Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF131720))
                                .clickable { launchEmail() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = "Email",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email",
                                    fontSize = 9.sp,
                                    color = CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = developerEmail,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard("Email", developerEmail, "Email copied!") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Email",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // GitHub Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF131720))
                                .clickable { launchGitHub() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = "GitHub",
                                tint = CyberGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub Profile",
                                    fontSize = 9.sp,
                                    color = CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "github.com/deepakkumar1115",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open GitHub",
                                tint = CyberGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Support Developer via UPI Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141926)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Support",
                                tint = CyberRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SUPPORT DEVELOPER (UPI)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "If you appreciate privacy-focused, zero-tracker utility tools, support independent development with a direct contribution via any UPI app.",
                            fontSize = 10.sp,
                            color = CyberTextSecondary,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // UPI ID Copyable Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0F1A))
                                .border(0.5.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "UPI ID / VPA",
                                    fontSize = 8.sp,
                                    color = CyberTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = upiId,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard("UPI ID", upiId, "UPI ID copied: $upiId") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy UPI ID",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Preset Tip Amount Chips
                        Text(
                            text = "CHOOSE CONTRIBUTION AMOUNT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tipPresets = listOf(
                                null to "Any",
                                50 to "₹50",
                                100 to "₹100",
                                250 to "₹250",
                                500 to "₹500"
                            )
                            tipPresets.forEach { (amount, label) ->
                                val isSelected = selectedTipAmount == amount
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ImmersiveActiveBlue else Color(0xFF1E2433))
                                        .border(
                                            1.dp,
                                            if (isSelected) CyberCyan else ImmersiveBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedTipAmount = amount }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) CyberCyan else Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary Pay via UPI Button
                        Button(
                            onClick = { launchUpiPayment() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("pay_via_upi_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = "Pay",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedTipAmount != null) "PAY ₹$selectedTipAmount VIA UPI" else "PAY VIA ANY UPI APP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Opens Google Pay, PhonePe, Paytm, BHIM, Cred, or your default UPI app",
                            fontSize = 8.sp,
                            color = CyberTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App Security / Integrity Note
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1218)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Shield",
                                tint = CyberGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AppData Blocker v1.0.0",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "100% on-device local VPN • Zero data collection • No trackers",
                                    fontSize = 9.sp,
                                    color = CyberTextSecondary
                                )
                            }
                        }

                        if (onOpenPrivacyPolicy != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onOpenPrivacyPolicy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("dialog_view_privacy_policy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Policy,
                                    contentDescription = "Policy",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VIEW PRIVACY POLICY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun FirstTimeWelcomeCard(
    onViewGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131926)),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp)
            )
            .testTag("first_time_welcome_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Welcome Icon",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "WELCOME TO APPDATA BLOCKER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "On-Device Dual-Shield Firewall",
                            fontSize = 10.sp,
                            color = CyberTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss Welcome Card",
                        tint = CyberTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Take complete control of background network traffic and block unauthorized trackers in 3 simple steps:",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Steps Row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WelcomeStepRow(number = "1", title = "Turn ON Firewall switch in the top bar")
                WelcomeStepRow(number = "2", title = "Enable Dual Shields (Installed / System)")
                WelcomeStepRow(number = "3", title = "Whitelist essential apps in Exemption Roster")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ImmersiveBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberTextSecondary),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("welcome_dismiss_button")
                ) {
                    Text(
                        text = "DISMISS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onViewGuide,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveActiveBlue,
                        contentColor = CyberCyan
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(38.dp)
                        .testTag("welcome_read_guide_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Book",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HOW TO USE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeStepRow(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(ImmersiveActiveBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            color = CyberTextSecondary
        )
    }
}

@Composable
fun HowToUseDashboardCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, ImmersiveBorder),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("how_to_use_dashboard_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2838)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "How to Use",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HOW TO USE & USER GUIDE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Learn how Dual Shields, Whitelisting & On-Device Security work",
                    fontSize = 10.sp,
                    color = CyberTextSecondary,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333538))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "GUIDE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Guide",
                    tint = CyberCyan,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUseDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(24.dp)),
        containerColor = CyberDarkBg,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImmersiveActiveBlue,
                    contentColor = CyberCyan
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("how_to_use_dialog_close_button")
            ) {
                Text(
                    text = "GOT IT, CLOSE GUIDE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Guide Icon",
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOW TO USE APPDATA BLOCKER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = CyberTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: Quick Start Guide
                GuideSectionCard(
                    title = "QUICK START (3 SIMPLE STEPS)",
                    icon = Icons.Filled.RocketLaunch,
                    accentColor = CyberCyan
                ) {
                    GuideStepItem(
                        step = "1",
                        title = "Turn On Protection",
                        desc = "Toggle the FIREWALL SWITCH in the top bar. When Android displays the connection prompt, tap OK to authorize the local firewall tunnel."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GuideStepItem(
                        step = "2",
                        title = "Choose Your Shields",
                        desc = "Enable Installed Apps Shield to block third-party app data, and System Apps Shield to block non-essential OEM bloatware."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GuideStepItem(
                        step = "3",
                        title = "Whitelist Apps You Need",
                        desc = "Keep essential apps (WhatsApp, Banking, Browser) connected by checking their box in the list or in the Exemption Roster (up to 11 apps)."
                    )
                }

                // Section 2: Dual Shields Explained
                GuideSectionCard(
                    title = "DUAL-SHIELD FIREWALL SYSTEM",
                    icon = Icons.Filled.Shield,
                    accentColor = CyberGreen
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .padding(top = 5.dp)
                                    .clip(CircleShape)
                                    .background(CyberOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Installed Apps Shield (Orange)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberOrange
                                )
                                Text(
                                    text = "Restricts internet access for all third-party downloaded apps. Prevents background data usage, stealth telemetry, and background ads.",
                                    fontSize = 10.sp,
                                    color = CyberTextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .padding(top = 5.dp)
                                    .clip(CircleShape)
                                    .background(CyberRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "System Apps Shield (Red)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberRed
                                )
                                Text(
                                    text = "Blocks non-essential OEM bloatware and vendor trackers. Essential OS services (Android framework, Google Play Services, Settings, NetworkStack) are automatically preserved so phone features never break.",
                                    fontSize = 10.sp,
                                    color = CyberTextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Section 3: Exemption Roster
                GuideSectionCard(
                    title = "EXEMPTION ROSTER (WHITELISTING)",
                    icon = Icons.Filled.VerifiedUser,
                    accentColor = CyberCyan
                ) {
                    Text(
                        text = "• Whitelisted Apps: Any app added to the roster completely bypasses the firewall and retains full, unrestricted internet access.\n" +
                               "• 11-App Quota: You can whitelist up to 11 critical apps. This keeps on-device memory and battery footprint exceptionally light.\n" +
                               "• Quick Toggles: Tap any checkbox on the dashboard or use the Exemption Roster dialog to search and manage whitelisted apps.",
                        fontSize = 10.sp,
                        color = CyberTextSecondary,
                        lineHeight = 15.sp
                    )
                }

                // Section 4: Status Badges
                GuideSectionCard(
                    title = "UNDERSTANDING STATUS BADGES",
                    icon = Icons.Filled.CheckCircle,
                    accentColor = CyberGreen
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeExplanationRow(
                            label = "EXEMPTED",
                            badgeColor = CyberGreen,
                            desc = "App is whitelisted and has full, unrestricted internet access."
                        )
                        BadgeExplanationRow(
                            label = "BLOCKED",
                            badgeColor = CyberRed,
                            desc = "App traffic is blackholed & dropped by the active shield."
                        )
                        BadgeExplanationRow(
                            label = "ACTIVE",
                            badgeColor = CyberCyan,
                            desc = "App has internet because its corresponding shield is OFF."
                        )
                    }
                }

                // Section 5: Privacy Guarantee
                GuideSectionCard(
                    title = "100% ON-DEVICE PRIVACY GUARANTEE",
                    icon = Icons.Filled.Lock,
                    accentColor = CyberGreen
                ) {
                    Text(
                        text = "• No Remote Servers: Unlike commercial VPN services, AppData Blocker processes traffic entirely inside device memory.\n" +
                               "• Zero Data Collection: No analytics, no accounts, and no browsing logs.\n" +
                               "• No Root Required: Operates strictly via Android's native VpnService security framework.",
                        fontSize = 10.sp,
                        color = CyberTextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    )
}

@Composable
fun GuideSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun GuideStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(ImmersiveActiveBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 10.sp,
                color = CyberTextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun BadgeExplanationRow(
    label: String,
    badgeColor: Color,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        StatusTag(label = label, color = badgeColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = desc,
            fontSize = 10.sp,
            color = CyberTextSecondary,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .fillMaxHeight(0.85f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = "Privacy Policy Icon",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PRIVACY POLICY",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "AppData Blocker • 100% On-Device",
                            fontSize = 10.sp,
                            color = CyberGreen
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp).testTag("close_privacy_policy_dialog")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Privacy Policy",
                        tint = CyberTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Key Summary Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E24)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Verified",
                            tint = CyberGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AppData Blocker does not collect, record, store, or transmit any personal data, network activity, or browsing history. Zero external servers. 100% local on-device operation.",
                            fontSize = 11.sp,
                            color = Color.White,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Android VpnService Disclosure
                PrivacyPolicySection(
                    title = "1. Android VpnService Disclosure",
                    icon = Icons.Filled.VpnKey,
                    content = "AppData Blocker uses the Android VpnService API solely to create a local on-device loopback network interface (10.1.10.1 and fd00::1). This allows the application to block or permit network connections on a per-app basis without requiring root access.\n\n" +
                            "• Zero Remote Proxies: Your network traffic is NEVER routed to external servers.\n" +
                            "• On-Device Blackhole: Packets from blocked applications are dropped locally before leaving your phone.\n" +
                            "• No Inspection or Decryption: AppData Blocker never decrypts HTTPS/TLS data, inspects packet contents, or monitors online activity."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Data Collection Policy
                PrivacyPolicySection(
                    title = "2. Data Collection Policy",
                    icon = Icons.Filled.NoAccounts,
                    content = "• Personal Information: NONE collected, stored, or transmitted.\n" +
                            "• Network & Web Activity: NONE logged, decrypted, or sent off-device.\n" +
                            "• Installed Apps List: Processed strictly in-memory on your device to enable firewall controls. Never uploaded.\n" +
                            "• Device Identifiers: No IMEI, MAC address, or Advertising IDs are accessed or collected."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: Device Permissions Explained
                PrivacyPolicySection(
                    title = "3. Device Permissions & Purpose",
                    icon = Icons.Filled.Lock,
                    content = "• INTERNET: Standard OS requirement to open the local firewall socket adapter.\n" +
                            "• QUERY_ALL_PACKAGES: Allows you to view and select installed apps to block or exempt.\n" +
                            "• FOREGROUND_SERVICE: Keeps the firewall running reliably in the background without getting killed by the OS.\n" +
                            "• POST_NOTIFICATIONS: Displays the active firewall status notification and one-tap stop control."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 4: Third-Party Trackers & Ads
                PrivacyPolicySection(
                    title = "4. Third-Party Trackers & Ads",
                    icon = Icons.Filled.Block,
                    content = "AppData Blocker contains NO third-party advertising SDKs, NO tracking cookies, and NO analytics frameworks. Voluntary support payments use the standard Android upi://pay intent, opening your existing banking or UPI app directly without AppData Blocker ever seeing your financial details."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 5: Developer Contact
                PrivacyPolicySection(
                    title = "5. Developer & Contact",
                    icon = Icons.Filled.ContactMail,
                    content = "Independent Developer: Deepak Kumar\n" +
                            "Email: deepakkumar1115@gmail.com\n" +
                            "GitHub: https://github.com/deepakkumar1115\n" +
                            "Application ID: com.appdatablocker.android\n" +
                            "Effective Date: November 9, 2026"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImmersiveActiveBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("dismiss_privacy_policy_button")
                ) {
                    Text(
                        text = "I UNDERSTAND & CLOSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun PrivacyPolicySection(
    title: String,
    icon: ImageVector,
    content: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ImmersiveBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                fontSize = 10.sp,
                color = CyberTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

