package com.novaforge.fpsplus.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaforge.fpsplus.PermissionSnapshot
import com.novaforge.fpsplus.monitor.HudOptions
import com.novaforge.fpsplus.monitor.MonitorSnapshot
import com.novaforge.fpsplus.monitor.MonitorState
import com.novaforge.fpsplus.monitor.PreferenceStore
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class AppStage { LOADING, PERMISSIONS, HOME }

@Composable
fun FPSPlusApp(
    permissions: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestBackground: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var stage by remember { mutableStateOf(AppStage.LOADING) }
    val latestPermissions by rememberUpdatedState(permissions)

    LaunchedEffect(Unit) {
        delay(9_000L)
        val completed = context.getSharedPreferences("fpsplus_onboarding", Context.MODE_PRIVATE)
            .getBoolean("complete", false)
        stage = if (completed && latestPermissions.allReady) AppStage.HOME else AppStage.PERMISSIONS
    }

    BackHandler(stage == AppStage.PERMISSIONS) { }
    AnimatedContent(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(420)) + slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 18 }) togetherWith
                (fadeOut(tween(260)) + slideOutVertically(tween(360)) { -it / 22 })
        },
        label = "app-stage-transition"
    ) { target ->
        when (target) {
            AppStage.LOADING -> LoadingScreen()
            AppStage.PERMISSIONS -> PermissionScreen(
                permissions = permissions,
                onRequestOverlay = onRequestOverlay,
                onRequestBackground = onRequestBackground,
                onRequestShizuku = onRequestShizuku,
                onRequestNotifications = onRequestNotifications,
                onContinue = {
                    onContinue()
                    stage = AppStage.HOME
                }
            )
            AppStage.HOME -> MainShell(
                onStartMonitor = onStartMonitor,
                onStopMonitor = onStopMonitor
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    val infinite = rememberInfiniteTransition(label = "loading-orbits")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7_500, easing = FastOutSlowInEasing)),
        label = "orbital-rotation"
    )
    val counterRotation by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = FastOutSlowInEasing)),
        label = "counter-orbit"
    )
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (progress < 1f) {
            progress = ((android.os.SystemClock.elapsedRealtime() - start) / 9_000f).coerceIn(0f, 1f)
            delay(24)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080C14), Color(0xFF0B1422), Color(0xFF080C14)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            for (i in 0..42) {
                val x = (i * 83f % w)
                val y = (i * 137f % h)
                val radius = if (i % 5 == 0) 2.2f else 1.1f
                drawCircle(Color(0xFF8FDCE8).copy(alpha = if (i % 3 == 0) 0.32f else 0.12f), radius, Offset(x, y))
            }
            val cx = w / 2f; val cy = h / 2f - 52.dp.toPx()
            drawCircle(Color(0xFF2FBDD0).copy(alpha = 0.07f), 164.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFF2FBDD0).copy(alpha = 0.055f), 126.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFF26394E), 93.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, FPSColors.Accent, FPSColors.AccentBlue, Color.Transparent)),
                startAngle = rotation,
                sweepAngle = 224f,
                useCenter = false,
                topLeft = Offset(cx - 104.dp.toPx(), cy - 104.dp.toPx()),
                size = Size(208.dp.toPx(), 208.dp.toPx()),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = FPSColors.AccentBlue.copy(alpha = 0.7f),
                startAngle = counterRotation,
                sweepAngle = 92f,
                useCenter = false,
                topLeft = Offset(cx - 121.dp.toPx(), cy - 121.dp.toPx()),
                size = Size(242.dp.toPx(), 242.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
            val a = Math.toRadians(rotation.toDouble())
            val orbX = cx + kotlin.math.cos(a).toFloat() * 104.dp.toPx()
            val orbY = cy + kotlin.math.sin(a).toFloat() * 104.dp.toPx()
            drawCircle(FPSColors.Accent.copy(alpha = 0.18f), 12.dp.toPx(), Offset(orbX, orbY))
            drawCircle(FPSColors.Accent, 4.dp.toPx(), Offset(orbX, orbY))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 34.dp)) {
            BrandMark(size = 74.dp)
            Spacer(Modifier.height(28.dp))
            Text("FPS+", color = FPSColors.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp)
            Text("PERFORMANCE OVERLAY", color = FPSColors.Accent, fontSize = 10.sp, letterSpacing = 3.1.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(205.dp))
            Text("INITIALIZING SYSTEM", color = FPSColors.Muted, fontSize = 10.sp, letterSpacing = 2.2.sp)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(0.62f).height(3.dp).clip(CircleShape).background(Color(0xFF1D2C3D))) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(FPSColors.AccentBlue, FPSColors.Accent)))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("${(progress * 100).roundToInt()}%  /  READYING TELEMETRY", color = FPSColors.Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF19384A), Color(0xFF111C2B))))
            .border(1.dp, FPSColors.Accent.copy(alpha = 0.42f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size * 0.76f)) {
            val w = this.size.width; val h = this.size.height
            val p = Path().apply {
                moveTo(w * 0.16f, h * 0.72f); lineTo(w * 0.16f, h * 0.22f)
                lineTo(w * 0.52f, h * 0.22f); lineTo(w * 0.52f, h * 0.35f)
                lineTo(w * 0.31f, h * 0.35f); lineTo(w * 0.31f, h * 0.46f)
                lineTo(w * 0.47f, h * 0.46f); lineTo(w * 0.47f, h * 0.58f)
                lineTo(w * 0.31f, h * 0.58f); lineTo(w * 0.31f, h * 0.72f); close()
            }
            drawPath(p, FPSColors.Accent)
            for (i in 0..3) {
                val barH = listOf(0.24f, 0.43f, 0.63f, 0.38f)[i]
                drawRoundRect(
                    color = if (i == 2) Color.White else FPSColors.AccentBlue,
                    topLeft = Offset(w * (0.62f + i * 0.09f), h * (0.72f - barH)),
                    size = Size(w * 0.055f, h * barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(
    permissions: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestBackground: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    val readyCount = listOf(permissions.overlay, permissions.background, permissions.shizuku, permissions.notifications).count { it }
    Box(Modifier.fillMaxSize().background(FPSColors.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF163A4C).copy(alpha = 0.24f), 220.dp.toPx(), Offset(size.width * 0.88f, size.height * 0.12f))
            drawCircle(Color(0xFF223B66).copy(alpha = 0.16f), 180.dp.toPx(), Offset(size.width * 0.02f, size.height * 0.82f))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FPS+", color = FPSColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("ONE-TIME SETUP", color = FPSColors.Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("$readyCount / 4", color = if (readyCount == 4) FPSColors.Good else FPSColors.Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(30.dp))
            Text("LET'S GET", color = FPSColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
            Text("PERMISSION READY.", color = FPSColors.Text, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
            Spacer(Modifier.height(9.dp))
            Text(
                "FPS+ needs a few system permissions to keep your live monitor visible while you play.",
                color = FPSColors.Muted, fontSize = 13.sp, lineHeight = 19.sp
            )
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.weight(1f)) {
                PermissionRow(
                    title = "Display over other apps",
                    description = "Required to show the floating FPS panel above games and apps.",
                    granted = permissions.overlay,
                    actionLabel = if (permissions.overlay) "ENABLED" else "ALLOW",
                    icon = { Icon(Icons.Outlined.Layers, null, tint = FPSColors.Accent, modifier = Modifier.size(20.dp)) },
                    onClick = onRequestOverlay
                )
                PermissionRow(
                    title = "Background operation",
                    description = "Allow FPS+ to stay active while another app is on screen.",
                    granted = permissions.background,
                    actionLabel = if (permissions.background) "ENABLED" else "ALLOW",
                    icon = { Icon(Icons.Outlined.Bolt, null, tint = FPSColors.Accent, modifier = Modifier.size(20.dp)) },
                    onClick = onRequestBackground
                )
                PermissionRow(
                    title = "Shizuku access",
                    description = if (permissions.shizukuRunning) "Shizuku is running. Approve FPS+ in its permission dialog." else "Start Shizuku first. Android 11+ can start it with Wireless debugging.",
                    granted = permissions.shizuku,
                    actionLabel = if (permissions.shizuku) "AUTHORIZED" else if (permissions.shizukuRunning) "AUTHORIZE" else "OPEN SHIZUKU",
                    icon = { Icon(Icons.Outlined.Security, null, tint = FPSColors.Accent, modifier = Modifier.size(20.dp)) },
                    onClick = onRequestShizuku
                )
                PermissionRow(
                    title = "Notifications",
                    description = "Keeps the monitoring service visible and gives you a stop control in notifications.",
                    granted = permissions.notifications,
                    actionLabel = if (permissions.notifications) "ENABLED" else "ALLOW",
                    icon = { Icon(Icons.Outlined.NotificationsActive, null, tint = FPSColors.Accent, modifier = Modifier.size(20.dp)) },
                    onClick = onRequestNotifications
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = FPSColors.Muted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shizuku must be started again after some device restarts when using Wireless debugging.", color = FPSColors.Muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
            AnimatedVisibility(
                visible = permissions.allReady,
                enter = fadeIn(tween(300)) + slideInVertically(tween(460, easing = FastOutSlowInEasing)) { it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it }
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FPSColors.Accent, contentColor = Color(0xFF06121A))
                ) {
                    Text("NEXT", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Spacer(Modifier.width(9.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
            if (!permissions.allReady) {
                Text("COMPLETE ALL PERMISSIONS TO CONTINUE", color = FPSColors.Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val stateColor = if (granted) FPSColors.Good else FPSColors.Warning
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2A)),
        border = BorderStroke(1.dp, if (granted) FPSColors.Good.copy(alpha = 0.22f) else FPSColors.Border)
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A2A3D)), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = FPSColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(description, color = FPSColors.Muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor))
                    Spacer(Modifier.height(7.dp))
                    Text(actionLabel, color = stateColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                }
            }
        }
    }
}

@Composable
private fun MainShell(onStartMonitor: () -> Unit, onStopMonitor: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snapshot by MonitorState.snapshot.collectAsState()
    val history by MonitorState.history.collectAsState()
    val options by PreferenceStore.options.collectAsState()
    androidx.compose.material3.Scaffold(
        containerColor = FPSColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1521), tonalElevation = 0.dp) {
                val labels = listOf("Overview", "Overlay", "Settings", "About")
                val icons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.Layers, Icons.Outlined.Tune, Icons.Outlined.Info)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 9.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(snapshot, history, options, onStartMonitor, onStopMonitor, { selectedTab = 1 })
                1 -> OverlaySettingsScreen(options)
                2 -> AppSettingsScreen(options)
                else -> AboutScreen()
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    snapshot: MonitorSnapshot,
    history: List<Int>,
    options: HudOptions,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOverlaySettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42.dp)
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("FPS+", color = FPSColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text("LIVE PERFORMANCE", color = FPSColors.Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(if (snapshot.isRunning) FPSColors.Good.copy(alpha = 0.12f) else FPSColors.SurfaceRaised).padding(horizontal = 11.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (snapshot.isRunning) FPSColors.Good else FPSColors.Muted))
                        Spacer(Modifier.width(6.dp))
                        Text(if (snapshot.isRunning) "ACTIVE" else "STANDBY", color = if (snapshot.isRunning) FPSColors.Good else FPSColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
        item {
            FpsHeroCard(snapshot, history)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("MEMORY", if (snapshot.ramTotalBytes > 0) "${bytesGb(snapshot.ramUsedBytes)} / ${bytesGb(snapshot.ramTotalBytes)} GB" else "—", "USED / TOTAL", Icons.Outlined.Memory, Modifier.weight(1f))
                MetricCard("BATTERY", if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "—", "REMAINING", Icons.Outlined.BatteryFull, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("DEVICE TEMP", if (snapshot.deviceTempC.isFinite()) "${snapshot.deviceTempC}°C" else "—", snapshot.tempSource.uppercase(), Icons.Outlined.Thermostat, Modifier.weight(1f))
                MetricCard("THERMAL", snapshot.thermalLabel, "ANDROID STATUS", Icons.Outlined.Bolt, Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = FPSColors.Surface), border = BorderStroke(1.dp, FPSColors.Border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("FOREGROUND APP", color = FPSColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(snapshot.topPackage, color = FPSColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(snapshot.fpsStatus, color = FPSColors.Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            Button(
                onClick = { if (snapshot.isRunning) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (snapshot.isRunning) Color(0xFF2A3545) else FPSColors.Accent,
                    contentColor = if (snapshot.isRunning) FPSColors.Text else Color(0xFF06121A)
                )
            ) {
                Icon(if (snapshot.isRunning) Icons.Outlined.WarningAmber else Icons.Outlined.Speed, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (snapshot.isRunning) "STOP MONITOR" else "START MONITOR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF101C29)).clickable(onClick = onOverlaySettings).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Tune, null, tint = FPSColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Customize floating HUD", color = FPSColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = FPSColors.Muted)
            }
        }
    }
}

@Composable
private fun FpsHeroCard(snapshot: MonitorSnapshot, history: List<Int>) {
    val fps = snapshot.fps
    val tint = fpsColor(fps)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101D2B)),
        border = BorderStroke(1.dp, FPSColors.Accent.copy(alpha = 0.2f))
    ) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(FPSColors.Accent.copy(alpha = 0.07f), 130.dp.toPx(), Offset(size.width * 0.92f, size.height * 0.04f))
                drawCircle(FPSColors.AccentBlue.copy(alpha = 0.05f), 95.dp.toPx(), Offset(size.width * 0.05f, size.height * 0.92f))
            }
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FRAME RATE", color = FPSColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                    Spacer(Modifier.weight(1f))
                    Text("REAL-TIME", color = FPSColors.Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fps?.toString() ?: "--", color = tint, fontSize = 62.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
                    Spacer(Modifier.width(9.dp))
                    Text("FPS", color = FPSColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawArc(Color(0xFF28394B), -90f, 360f, false, style = Stroke(5.dp.toPx()))
                            val sweep = ((fps ?: 0).coerceIn(0, 120) / 120f) * 360f
                            drawArc(tint, -90f, sweep, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Icon(Icons.Outlined.Speed, null, tint = tint, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(snapshot.fpsStatus, color = FPSColors.Muted, fontSize = 10.sp)
                Spacer(Modifier.height(16.dp))
                Text("FRAME TREND", color = FPSColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                PerformanceGraph(history, tint, Modifier.fillMaxWidth().height(62.dp))
            }
        }
    }
}

@Composable
private fun PerformanceGraph(history: List<Int>, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gridColor = Color(0xFF26384A)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        if (history.size < 2) {
            drawLine(tint.copy(alpha = 0.25f), Offset(0f, size.height * 0.72f), Offset(size.width, size.height * 0.72f), 1.5.dp.toPx())
            return@Canvas
        }
        val maxVal = maxOf(60, history.maxOrNull() ?: 60).toFloat()
        val minVal = 0f
        val path = Path()
        history.forEachIndexed { index, value ->
            val x = (index.toFloat() / (history.size - 1)) * size.width
            val y = size.height - ((value - minVal) / (maxVal - minVal)) * size.height * 0.88f - size.height * 0.06f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint.copy(alpha = 0.16f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, tint, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        val lastX = size.width
        val lastY = size.height - ((history.last() - minVal) / (maxVal - minVal)) * size.height * 0.88f - size.height * 0.06f
        drawCircle(tint.copy(alpha = 0.22f), 8.dp.toPx(), Offset(lastX, lastY))
        drawCircle(tint, 3.4.dp.toPx(), Offset(lastX, lastY))
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FPSColors.Surface),
        border = BorderStroke(1.dp, FPSColors.Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = FPSColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, color = FPSColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Text(value, color = FPSColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = FPSColors.Muted, fontSize = 8.sp, letterSpacing = 0.9.sp)
        }
    }
}

@Composable
private fun OverlaySettingsScreen(options: HudOptions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { ScreenHeader("FLOATING HUD", "Choose the data shown over your apps") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2A)), border = BorderStroke(1.dp, FPSColors.Accent.copy(alpha = 0.2f))) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("LIVE PREVIEW", color = FPSColors.Muted, fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF0A111B)), contentAlignment = Alignment.Center) {
                        HudOverlay(
                            MonitorSnapshot(fps = 60, fpsStatus = "Preview", ramUsedBytes = 4_100_000_000, ramTotalBytes = 8_000_000_000, batteryPercent = 84, batteryTempC = 34.2f, deviceTempC = 34.2f, tempSource = "CPU", thermalLabel = "Cool", topPackage = "com.game.preview"),
                            options.copy(scale = (options.scale * 0.82f).coerceIn(0.65f, 1.2f), opacity = 1f)
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Preview only · real values appear when the monitor is active", color = FPSColors.Muted, fontSize = 9.sp)
                }
            }
        }
        item { SectionLabel("DISPLAY DATA") }
        item { OptionSwitch("FPS counter", "Show live frames per second", options.showFps) { v -> PreferenceStore.update(context) { it.copy(showFps = v) } } }
        item { OptionSwitch("Memory usage", "Used RAM and total capacity", options.showRam) { v -> PreferenceStore.update(context) { it.copy(showRam = v) } } }
        item { OptionSwitch("Battery level", "Current battery percentage", options.showBattery) { v -> PreferenceStore.update(context) { it.copy(showBattery = v) } } }
        item { OptionSwitch("Device temperature", "CPU/GPU sensor when exposed; otherwise battery sensor", options.showTemp) { v -> PreferenceStore.update(context) { it.copy(showTemp = v) } } }
        item { OptionSwitch("Foreground app", "Show the active app package", options.showApp) { v -> PreferenceStore.update(context) { it.copy(showApp = v) } } }
        item { OptionSwitch("Thermal status", "Android thermal throttling status", options.showThermal) { v -> PreferenceStore.update(context) { it.copy(showThermal = v) } } }
        item { SectionLabel("PANEL SIZE") }
        item {
            SliderCard(
                title = "HUD scale", valueLabel = "${(options.scale * 100).roundToInt()}%",
                value = options.scale, range = 0.75f..1.45f
            ) { v -> PreferenceStore.update(context) { it.copy(scale = v) } }
        }
        item {
            SliderCard(
                title = "Panel opacity", valueLabel = "${(options.opacity * 100).roundToInt()}%",
                value = options.opacity, range = 0.45f..1f
            ) { v -> PreferenceStore.update(context) { it.copy(opacity = v) } }
        }
        item { OptionSwitch("Compact panel", "Reduce spacing and panel height", options.compact) { v -> PreferenceStore.update(context) { it.copy(compact = v) } } }
        item { OptionSwitch("Accent border", "Show a subtle cyan outline", options.showBorder) { v -> PreferenceStore.update(context) { it.copy(showBorder = v) } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { PreferenceStore.update(context) { it.copy(x = 24, y = 120) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = FPSColors.SurfaceRaised, contentColor = FPSColors.Text)) {
                    Text("RESET POSITION", fontSize = 9.sp, letterSpacing = 0.8.sp)
                }
                Button(onClick = { PreferenceStore.update(context) { it.copy(showFps = true, showRam = false, showBattery = false, showTemp = false, showApp = false, showThermal = false) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = FPSColors.SurfaceRaised, contentColor = FPSColors.Text)) {
                    Text("FPS ONLY", fontSize = 9.sp, letterSpacing = 0.8.sp)
                }
            }
        }
    }
}

@Composable
private fun AppSettingsScreen(options: HudOptions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { ScreenHeader("PREFERENCES", "Fine-tune monitoring behavior") }
        item { SectionLabel("UPDATE ENGINE") }
        item {
            SliderCard(
                title = "Refresh interval", valueLabel = "${options.refreshMs} ms",
                value = options.refreshMs.toFloat(), range = 500f..3000f,
                steps = 4
            ) { v -> PreferenceStore.update(context) { it.copy(refreshMs = (v / 500f).roundToInt() * 500) } }
        }
        item {
            Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = FPSColors.Surface), border = BorderStroke(1.dp, FPSColors.Border)) {
                Column(Modifier.padding(15.dp)) {
                    Text("POLLING GUIDANCE", color = FPSColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("500 ms is more responsive but uses more power. 1,000–2,000 ms is a balanced choice for long gaming sessions.", color = FPSColors.Muted, fontSize = 10.sp, lineHeight = 16.sp)
                }
            }
        }
        item { SectionLabel("DISPLAY BEHAVIOR") }
        item { OptionSwitch("Keep compact layout", "Apply a denser HUD style", options.compact) { v -> PreferenceStore.update(context) { it.copy(compact = v) } } }
        item { OptionSwitch("Show accent outline", "Draw a fine cyan edge around the HUD", options.showBorder) { v -> PreferenceStore.update(context) { it.copy(showBorder = v) } } }
        item { SectionLabel("DEVICE SUPPORT") }
        item {
            InfoRow("FPS source", "SurfaceFlinger frame timestamps via Shizuku")
            InfoRow("RAM source", "Android ActivityManager memory snapshot")
            InfoRow("Battery source", "System battery broadcast")
            InfoRow("Thermal source", "PowerManager thermal status API")
        }
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(58.dp)
                Spacer(Modifier.width(15.dp))
                Column {
                    Text("FPS+", color = FPSColors.Text, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                    Text("VERSION 1.0.0 · ANDROID", color = FPSColors.Accent, fontSize = 9.sp, letterSpacing = 1.3.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = FPSColors.Surface), border = BorderStroke(1.dp, FPSColors.Border)) {
                Column(Modifier.padding(16.dp)) {
                    Text("BUILT FOR THE MOMENT", color = FPSColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("A lightweight floating performance panel for games and everyday apps. FPS+, memory, battery and thermal information are sampled on-device.", color = FPSColors.Muted, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
        }
        item { SectionLabel("IMPORTANT NOTES") }
        item { InfoRow("FPS accuracy", "Depends on SurfaceFlinger exposing the selected app layer. Some OEM builds restrict this even with Shizuku.") }
        item { InfoRow("Temperature readings", "CPU/GPU sensors are read from ThermalService when the device exposes them. Otherwise FPS+ falls back to the battery temperature sensor.") }
        item { InfoRow("Background behavior", "Battery optimization exceptions improve continuity, but OEM power managers may still stop background services.") }
        item { InfoRow("Privacy", "No account, analytics SDK, Firebase, or remote telemetry is included. Stats are read locally on the device.") }
        item {
            Text("FPS+  /  PERFORMANCE, WITHOUT THE CLUTTER", color = FPSColors.Muted, fontSize = 8.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = FPSColors.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = FPSColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = FPSColors.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, modifier = Modifier.padding(top = 4.dp, bottom = 1.dp))
}

@Composable
private fun OptionSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(FPSColors.Surface)
            .border(1.dp, FPSColors.Border, RoundedCornerShape(17.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = FPSColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(description, color = FPSColors.Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(FPSColors.Surface).border(1.dp, FPSColors.Border, RoundedCornerShape(17.dp)).padding(horizontal = 15.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = FPSColors.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueLabel, color = FPSColors.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = FPSColors.Accent,
                activeTrackColor = FPSColors.Accent,
                inactiveTrackColor = Color(0xFF2A3A4D)
            )
        )
    }
}

@Composable
private fun InfoRow(title: String, description: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FPSColors.Surface).padding(14.dp)) {
        Text(title, color = FPSColors.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(description, color = FPSColors.Muted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
fun HudOverlay(snapshot: MonitorSnapshot, options: HudOptions) {
    val animatedScale by animateFloatAsState(options.scale, animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f), label = "hud-scale")
    val animatedAlpha by animateFloatAsState(options.opacity, animationSpec = tween(220), label = "hud-opacity")
    val hasAny = options.showFps || options.showRam || options.showBattery || options.showTemp || options.showApp || options.showThermal
    if (!hasAny) return
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density * animatedScale, baseDensity.fontScale)) {
        Card(
            modifier = Modifier.widthIn(min = 104.dp, max = 245.dp).graphicsLayer(alpha = animatedAlpha),
            shape = RoundedCornerShape(if (options.compact) 11.dp else 15.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xE90B1420)),
            border = if (options.showBorder) BorderStroke(1.dp, FPSColors.Accent.copy(alpha = 0.45f)) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(Modifier.padding(horizontal = if (options.compact) 10.dp else 13.dp, vertical = if (options.compact) 7.dp else 10.dp)) {
                if (options.showFps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FPS:", color = FPSColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(snapshot.fps?.toString() ?: "0", color = fpsColor(snapshot.fps), fontSize = if (options.compact) 17.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (snapshot.fps != null) FPSColors.Good else FPSColors.Warning))
                    }
                }
                if (options.showFps && (options.showRam || options.showBattery || options.showTemp || options.showApp || options.showThermal)) {
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    Spacer(Modifier.height(5.dp))
                }
                if (options.showRam) HudLine("RAM", if (snapshot.ramTotalBytes > 0) "${bytesGb(snapshot.ramUsedBytes)} / ${bytesGb(snapshot.ramTotalBytes)} GB" else "—")
                if (options.showBattery) HudLine("BAT", if (snapshot.batteryPercent >= 0) "${snapshot.batteryPercent}%" else "—")
                if (options.showTemp) HudLine("TEMP", if (snapshot.deviceTempC.isFinite()) "${snapshot.deviceTempC}°C" else "—")
                if (options.showThermal) HudLine("THERMAL", snapshot.thermalLabel)
                if (options.showApp) HudLine("APP", snapshot.topPackage.substringAfterLast('.').take(18))
            }
        }
    }
}

@Composable
private fun HudLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = FPSColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = FPSColors.Text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun fpsColor(fps: Int?): Color = when {
    fps == null -> FPSColors.Muted
    fps in 5..10 -> Color(0xFFFF4F67)
    fps in 11..20 -> Color(0xFFFF9B45)
    fps in 21..40 -> Color(0xFFFFD45E)
    fps in 41..50 -> Color(0xFF55D99A)
    fps >= 51 -> Color(0xFF079B68)
    else -> FPSColors.Muted
}

private fun bytesGb(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_000_000_000.0)
