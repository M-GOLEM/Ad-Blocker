package com.adblocker.app

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adblocker.app.ui.screens.HomeScreen
import com.adblocker.app.ui.screens.SettingsScreen
import com.adblocker.app.ui.screens.StatsScreen
import com.adblocker.app.ui.theme.AdBlockerTheme
import com.adblocker.app.ui.theme.LocalPalette
import com.adblocker.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val dark by vm.darkMode.collectAsState()
    AdBlockerTheme(dark = dark) {
        MainScaffold(vm)
    }
}

@Composable
fun MainScaffold(vm: MainViewModel) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val running by vm.running.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // The background gradient drifts very slowly (no circle shapes).
    val drift by rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.start()
    }

    val onToggle: () -> Unit = {
        if (running) {
            vm.stop()
        } else {
            val intent = VpnService.prepare(context)
            if (intent == null) vm.start() else vpnLauncher.launch(intent)
        }
    }

    val items = listOf(
        Triple(0, "Protection", R.drawable.ic_shield),
        Triple(1, "Statistics", R.drawable.ic_chart),
        Triple(2, "Settings", R.drawable.ic_gear)
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = p.card[0]) {
                items.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(painterResource(icon), contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = p.accent,
                            selectedTextColor = p.accent,
                            unselectedIconColor = p.muted,
                            unselectedTextColor = p.muted,
                            indicatorColor = p.accent.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = p.bg,
                        start = Offset(drift * 600f, 0f),
                        end = Offset(900f - drift * 600f, 1800f)
                    )
                )
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = tab,
                label = "tabs",
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(420)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(420)))
                        .togetherWith(
                            slideOutHorizontally(tween(420)) { if (forward) -it / 4 else it / 4 } +
                                fadeOut(tween(300))
                        )
                }
            ) { t ->
                when (t) {
                    0 -> HomeScreen(vm, onToggle)
                    1 -> StatsScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}
