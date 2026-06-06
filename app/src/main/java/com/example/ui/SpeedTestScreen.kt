package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.SpeedTestResult
import com.example.network.NetworkInfo
import com.example.network.NetworkType
import com.example.network.SpeedTestState
import com.example.network.TestPhase
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    viewModel: SpeedTestViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkInfo by viewModel.networkInfo.collectAsState()
    val speedTestState by viewModel.speedTestState.collectAsState()
    val historyResults by viewModel.historyResults.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Check location permission state for Wi-Fi tracking SSID values
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        viewModel.refreshNetworkInfo()
    }

    LaunchedEffect(hasPermission) {
        viewModel.refreshNetworkInfo()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CyberDarkBg, Color(0xFF07090F)))),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = com.example.R.string.app_name),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = CyberPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.testTag("app_title")
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshNetworkInfo() },
                        modifier = Modifier.testTag("refresh_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Obnovit informace",
                            tint = CyberPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            CustomNavigationBar(
                selectedIndex = activeTab,
                onTabSelected = { activeTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (activeTab) {
                0 -> SpeedTab(
                    speedTestState = speedTestState,
                    networkInfo = networkInfo,
                    onStartTest = { viewModel.startSpeedTest() }
                )
                1 -> SignalTab(
                    networkInfo = networkInfo,
                    hasPermission = hasPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
                2 -> HistoryTab(
                    historyResults = historyResults,
                    onDeleteResult = { viewModel.deleteTestResult(it) },
                    onClearAll = { showClearConfirm = true }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Color(0xFF151829),
            title = {
                Text(
                    text = "Vymazat historii?",
                    color = CyberWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Opravdu chcete smazat všechna předchozí měření? Tuto akci nelze vzít zpět.",
                    color = CyberGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text(text = "Smazat vše", color = CyberTertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = "Zrušit", color = CyberGray)
                }
            }
        )
    }
}

@Composable
fun CustomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("bottom_nav_bar"),
        color = CyberCardBg,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, ImmersiveDivider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationBarItem(
                selected = selectedIndex == 0,
                icon = Icons.Filled.Speed,
                label = stringResource(id = com.example.R.string.tab_speed),
                onClick = { onTabSelected(0) },
                tag = "tab_btn_speed"
            )
            NavigationBarItem(
                selected = selectedIndex == 1,
                icon = Icons.Filled.SignalCellularAlt,
                label = stringResource(id = com.example.R.string.tab_signal),
                onClick = { onTabSelected(1) },
                tag = "tab_btn_signal"
            )
            NavigationBarItem(
                selected = selectedIndex == 2,
                icon = Icons.Filled.History,
                label = stringResource(id = com.example.R.string.tab_history),
                onClick = { onTabSelected(2) },
                tag = "tab_btn_history"
            )
        }
    }
}

@Composable
fun NavigationBarItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) CyberPrimary else CyberGray,
        animationSpec = twin(200)
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f
    )

    Column(
        modifier = Modifier
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 30.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) CyberSecondary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}



@Composable
fun SpeedTab(
    speedTestState: SpeedTestState,
    networkInfo: NetworkInfo,
    onStartTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("speed_tab_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Network label summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, ImmersiveDivider),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyberSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (networkInfo.type) {
                            NetworkType.WIFI -> Icons.Filled.Wifi
                            NetworkType.MOBILE -> Icons.Filled.SignalCellularAlt
                            else -> Icons.Filled.Warning
                        },
                        contentDescription = "Připojení",
                        tint = if (networkInfo.type == NetworkType.NONE) CyberTertiary else CyberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = networkInfo.name,
                        color = CyberWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when (networkInfo.type) {
                            NetworkType.WIFI -> stringResource(id = com.example.R.string.net_wifi)
                            NetworkType.MOBILE -> stringResource(id = com.example.R.string.net_mobile)
                            else -> stringResource(id = com.example.R.string.net_none)
                        },
                        color = CyberGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Circular Speedometer Gauge
        SpeedometerGauge(
            currentSpeed = speedTestState.currentSpeedMbps,
            phase = speedTestState.phase,
            onStart = onStartTest
        )

        // Running status title
        Text(
            text = when (speedTestState.phase) {
                TestPhase.IDLE -> stringResource(id = com.example.R.string.status_idle)
                TestPhase.PING -> stringResource(id = com.example.R.string.status_pinging)
                TestPhase.DOWNLOAD -> stringResource(id = com.example.R.string.status_downloading)
                TestPhase.UPLOAD -> stringResource(id = com.example.R.string.status_uploading)
                TestPhase.COMPLETED -> stringResource(id = com.example.R.string.status_completed)
                TestPhase.FAILED -> speedTestState.errorMessage ?: stringResource(id = com.example.R.string.status_failed)
            },
            color = when (speedTestState.phase) {
                TestPhase.FAILED -> CyberTertiary
                TestPhase.COMPLETED -> CyberSecondary
                TestPhase.IDLE -> CyberWhite
                else -> CyberPrimary
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Metrics details Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricResultCard(
                title = stringResource(id = com.example.R.string.text_ping),
                value = if (speedTestState.pingMs > 0) "${speedTestState.pingMs} ms" else "--",
                icon = Icons.Filled.Refresh,
                accentColor = CyberPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricResultCard(
                title = stringResource(id = com.example.R.string.text_jitter),
                value = if (speedTestState.jitterMs > 0) "${speedTestState.jitterMs} ms" else "--",
                icon = Icons.Filled.Info,
                accentColor = CyberPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricResultCard(
                title = stringResource(id = com.example.R.string.text_download),
                value = if (speedTestState.finalDownloadMbps > 0) String.format(Locale.US, "%.1f", speedTestState.finalDownloadMbps) + " Mb/s" else if (speedTestState.phase == TestPhase.DOWNLOAD) String.format(Locale.US, "%.1f", speedTestState.currentSpeedMbps) + " Mb/s" else "--",
                icon = Icons.Filled.ArrowDownward,
                accentColor = CyberPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricResultCard(
                title = stringResource(id = com.example.R.string.text_upload),
                value = if (speedTestState.finalUploadMbps > 0) String.format(Locale.US, "%.1f", speedTestState.finalUploadMbps) + " Mb/s" else if (speedTestState.phase == TestPhase.UPLOAD) String.format(Locale.US, "%.1f", speedTestState.currentSpeedMbps) + " Mb/s" else "--",
                icon = Icons.Filled.ArrowUpward,
                accentColor = CyberPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun twin(duration: Int): AnimationSpec<Color> = tween(durationMillis = duration)

@Composable
fun SpeedometerGauge(
    currentSpeed: Double,
    phase: TestPhase,
    onStart: () -> Unit
) {
    val maxDialSpeed = 250.0 // 250 Mbps full scale log bounds

    // Smooth needle movement with responsive spring
    val needleTarget = if (phase == TestPhase.DOWNLOAD || phase == TestPhase.UPLOAD) {
        currentSpeed
    } else {
        0.0
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = needleTarget.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .testTag("speed_gauge_container"),
        contentAlignment = Alignment.Center
    ) {
        // Gauge Background & Dial drawing
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(x = canvasWidth / 2, y = canvasHeight / 2)
            val radius = canvasWidth / 2 - 16.dp.toPx()

            // Outer dark track rim glow
            drawArc(
                color = Color(0xFF1E233D),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Calculate current sweep fraction logarithmically: (ln(speed + 1) / ln(maxSpeed))
            val speedFraction = if (animatedSpeed > 0) {
                (Math.log(animatedSpeed.toDouble() + 1) / Math.log(maxDialSpeed + 1)).coerceIn(0.0, 1.0).toFloat()
            } else {
                0.0f
            }

            // Foreground neon gradient track
            val activeBrush = Brush.sweepGradient(
                colors = listOf(
                    CyberPrimary,
                    CyberSecondary,
                    CyberTertiary,
                    CyberPrimary // closed cycle fix
                ),
                center = center
            )

            // Draw progress arc with visual clipping segment matching start 135 to current sweep
            drawArc(
                brush = activeBrush,
                startAngle = 135f,
                sweepAngle = speedFraction * 270f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Ticks marking
            val tickCount = 11
            for (i in 0 until tickCount) {
                val tickFraction = i.toFloat() / (tickCount - 1)
                val tickAngle = 135f + tickFraction * 270f
                val angleRad = Math.toRadians(tickAngle.toDouble())
                
                val innerLength = if (i % 2 == 0) 14.dp.toPx() else 8.dp.toPx()
                val tickWidth = if (i % 2 == 0) 3.dp.toPx() else 1.5f.dp.toPx()
                val tickColor = if (tickFraction <= speedFraction) CyberSecondary else CyberGray.copy(alpha = 0.4f)

                val startOffset = Offset(
                    x = center.x + (radius - 2.dp.toPx()) * cos(angleRad).toFloat(),
                    y = center.y + (radius - 2.dp.toPx()) * sin(angleRad).toFloat()
                )
                val endOffset = Offset(
                    x = center.x + (radius - 2.dp.toPx() - innerLength) * cos(angleRad).toFloat(),
                    y = center.y + (radius - 2.dp.toPx() - innerLength) * sin(angleRad).toFloat()
                )

                drawLine(
                    color = tickColor,
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = tickWidth
                )
            }

            // Draw Physics Needle pointer
            val needleAngle = 135f + speedFraction * 270f
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleLength = radius - 24.dp.toPx()
            val needleEnd = Offset(
                x = center.x + needleLength * cos(needleRad).toFloat(),
                y = center.y + needleLength * sin(needleRad).toFloat()
            )

            // Draw glowing core hub
            drawCircle(
                color = Color(0xFF13172E),
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = CyberPrimary,
                radius = 10.dp.toPx(),
                center = center
            )

            // Draw elegant tapering speed needle line
            drawLine(
                color = CyberSecondary,
                start = center,
                end = needleEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Central visual components overlay: Digital readout or SPUSTIT button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            if (phase == TestPhase.IDLE || phase == TestPhase.COMPLETED || phase == TestPhase.FAILED) {
                // Interactive start pulse button in the middle
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(CyberPrimary) // Immersive Lavender background
                        .border(
                            BorderStroke(
                                2.dp,
                                CyberWhite
                            ),
                            CircleShape
                        )
                        .clickable { onStart() }
                        .testTag("start_test_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Spustit",
                            tint = CyberSecondary, // Deep Indigo
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = com.example.R.string.btn_start),
                            color = CyberSecondary, // Deep Indigo
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Real-time speed numeric display
                Text(
                    text = String.format(Locale.US, "%.1f", currentSpeed),
                    fontSize = 42.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light, // light-font tracking-tighter as styled in HTML
                    color = CyberWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("running_speed_text")
                )
                Text(
                    text = "Mbps",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberPrimary, // Elegant lavender
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Mini pulse loading bar inside
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CyberPrimary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Composable
fun MetricResultCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, ImmersiveDivider),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = CyberGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = CyberWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SignalTab(
    networkInfo: NetworkInfo,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("signal_tab_content"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = com.example.R.string.title_signal),
            color = CyberWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        // Custom Graphic: Dynamic bar indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, ImmersiveDivider),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Signal percentage calculation or visual dial representation
                val activeBars = networkInfo.signalLevel
                
                Row(
                    modifier = Modifier.height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 1..4) {
                        val active = i <= activeBars
                        val barHeight = (22 * i).dp
                        val barColor = if (active) {
                            when (activeBars) {
                                1 -> CyberTertiary
                                2, 3 -> CyberPrimary
                                4 -> CyberSecondary
                                else -> CyberGray
                            }
                        } else Color(0xFF1E2339)

                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(barColor)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (networkInfo.signalLevel) {
                        0 -> "Vynikající klid nebo odpojeno"
                        1 -> "Slabý signál"
                        2 -> "Průměrný signál"
                        3 -> "Dobrý signál"
                        4 -> "Vynikající pevný signál"
                        else -> "Neznámí"
                    },
                    color = CyberWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                if (networkInfo.signalDbm != null) {
                    Text(
                        text = "${networkInfo.signalDbm} dBm",
                        color = CyberPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Connection detail metrics
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, ImmersiveDivider)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                DetailItem(label = "Síť", value = networkInfo.name)
                DetailItem(
                    label = "Typ připojení",
                    value = when (networkInfo.type) {
                        NetworkType.WIFI -> "Wi-Fi (Bezdrátová)"
                        NetworkType.MOBILE -> "Mobilní Přenos"
                        else -> "Žádné"
                    }
                )
                
                if (networkInfo.type == NetworkType.WIFI) {
                    networkInfo.frequencyGhz?.let { freq ->
                        DetailItem(label = "Pásmo / Frekvence", value = "$freq GHz")
                    }
                    networkInfo.linkSpeedMbps?.let { speed ->
                        DetailItem(label = "Linková kapacita", value = "$speed Mbps")
                    }
                }
                
                networkInfo.ipAddress?.let { ip ->
                    DetailItem(label = "Lokální IP adresa", value = ip)
                }
            }
        }

        // Location permission disclaimer for SSID details
        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x33FFA000)),
                border = BorderStroke(1.dp, CyberAmber),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = com.example.R.string.permission_rationale),
                        color = CyberWhite,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("grant_permission_button")
                    ) {
                        Text(
                            text = stringResource(id = com.example.R.string.grant_permission),
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = CyberGray, fontSize = 13.sp)
        Text(text = value, color = CyberWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryTab(
    historyResults: List<SpeedTestResult>,
    onDeleteResult: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_tab_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = com.example.R.string.title_history),
                color = CyberWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (historyResults.isNotEmpty()) {
                Text(
                    text = stringResource(id = com.example.R.string.clear_history),
                    color = CyberTertiary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onClearAll() }
                        .testTag("clear_history_label")
                )
            }
        }

        if (historyResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = CyberGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = com.example.R.string.empty_history),
                        color = CyberGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = historyResults,
                    key = { it.id }
                ) { result ->
                    HistoryItemCard(
                        result = result,
                        onDelete = { onDeleteResult(result.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    result: SpeedTestResult,
    onDelete: () -> Unit
) {
    val date = remember(result.timestamp) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(result.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${result.id}"),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, ImmersiveDivider),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (result.networkType == "Wi-Fi") Icons.Filled.Wifi else Icons.Filled.SignalCellularAlt,
                        contentDescription = null,
                        tint = CyberPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = result.networkName,
                        color = CyberWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = date,
                    color = CyberGray,
                    fontSize = 11.sp
                )
                
                // Speed summary grid inside card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SpeedValueIndicator(
                        label = "Down",
                        value = "${String.format(Locale.US, "%.1f", result.downloadSpeedMbps)} Mb/s",
                        accentColor = CyberSecondary
                    )
                    SpeedValueIndicator(
                        label = "Up",
                        value = "${String.format(Locale.US, "%.1f", result.uploadSpeedMbps)} Mb/s",
                        accentColor = CyberAmber
                    )
                    SpeedValueIndicator(
                        label = "Ping",
                        value = "${result.pingMs} ms",
                        accentColor = CyberPrimary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_item_btn_${result.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Smazat",
                    tint = CyberTertiary.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SpeedValueIndicator(
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Text(text = label, color = CyberGray, fontSize = 10.sp)
        Text(text = value, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
