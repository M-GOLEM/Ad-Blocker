package com.adblocker.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.app.R
import com.adblocker.app.data.FilterCatalog
import com.adblocker.app.ui.components.AnimatedCounter
import com.adblocker.app.ui.components.DedicationCard
import com.adblocker.app.ui.components.GlassCard
import com.adblocker.app.ui.components.Reveal
import com.adblocker.app.ui.components.ShimmerBar
import com.adblocker.app.ui.components.pressScale
import com.adblocker.app.ui.theme.LocalPalette
import com.adblocker.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val running by vm.running.collectAsState()
    val preparing by vm.preparing.collectAsState()
    val status by vm.status.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val selected by vm.selected.collectAsState()
    val level = FilterCatalog.levels.firstOrNull { it.ids == selected }?.name ?: "Custom"

    // Animated dots after "Preparing protection" (. .. ...)
    val dots by produceState(initialValue = 0, key1 = preparing) {
        if (!preparing) return@produceState
        while (true) {
            delay(350)
            value = (value + 1) % 4
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Ad Blocker",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = TextStyle(brush = Brush.horizontalGradient(listOf(p.accent, p.accent2)))
                )
                Text(
                    "Block ads in all your apps with one tap",
                    color = p.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Reveal(1) { PowerButton(running = running, preparing = preparing, onClick = onToggle) }
        Spacer(Modifier.height(8.dp))

        val stateKey = when {
            preparing -> 2
            running -> 1
            else -> 0
        }
        AnimatedContent(
            targetState = stateKey,
            label = "state",
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
            }
        ) { st ->
            val text = when (st) {
                2 -> "Preparing protection" + ".".repeat(dots)
                1 -> "Protection is active"
                else -> "Tap to start protection"
            }
            val color = when (st) {
                2 -> p.accent2
                1 -> p.accent
                else -> p.text
            }
            Text(text, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (preparing) {
            Spacer(Modifier.height(8.dp))
            ShimmerBar(Modifier.width(180.dp).height(6.dp), color = p.accent2)
        } else {
            Crossfade(targetState = status, label = "status") { s ->
                Text(s, color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(12.dp))
        Reveal(2) {
            GlassCard(
                Modifier.fillMaxWidth(),
                glow = p.danger,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_block),
                    contentDescription = null,
                    tint = p.danger,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(6.dp))
                AnimatedCounter(value = blocked, color = p.text, fontSize = 46.sp)
                Text("Ads blocked", color = p.muted, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Reveal(3, Modifier.weight(1f)) {
                InfoTile("Protection level", level, R.drawable.ic_shield, p.accent)
            }
            Reveal(4, Modifier.weight(1f)) {
                InfoTile(
                    "Status",
                    if (running) "Active" else "Stopped",
                    R.drawable.ic_bolt,
                    if (running) p.accent2 else p.muted
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Reveal(5) { DedicationCard() }
    }
}

@Composable
private fun InfoTile(title: String, value: String, icon: Int, color: Color) {
    val p = LocalPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glow = color,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(value, color = p.text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(title, color = p.muted, fontSize = 11.sp)
    }
}

@Composable
private fun PowerButton(running: Boolean, preparing: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }

    val transition = rememberInfiniteTransition(label = "power")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val arcSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "arcSpin"
    )

    val accent by animateColorAsState(if (running) p.accent else p.muted, tween(600), label = "accent")
    val accent2 by animateColorAsState(if (running) p.accent2 else p.card[1], tween(600), label = "accent2")
    val ringAlpha by animateFloatAsState(if (running) 0.7f else 0f, tween(500), label = "ring")
    val coreScale by animateFloatAsState(
        targetValue = when {
            preparing -> 0.94f
            running -> 1.06f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "core"
    )

    Box(
        modifier = Modifier
            .size(196.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (running) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0..1) {
                    val f = (pulse + i * 0.5f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = 0.35f * (1f - f)),
                        radius = size.minDimension / 2 * (0.5f + 0.5f * f)
                    )
                }
            }
        }

        // Rotating arc while preparing (spins until protection is ready)
        if (preparing) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = arcSpin }) {
                val r = size.minDimension / 2 - 4.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(listOf(p.accent2, p.accent, p.accent2)),
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }) {
            drawCircle(
                color = accent.copy(alpha = ringAlpha),
                radius = size.minDimension / 2 - 8.dp.toPx(),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
                )
            )
        }

        Box(
            modifier = Modifier
                .size(178.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (running) 0.22f else 0.05f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2
                        )
                    )
                }
        )

        Box(
            modifier = Modifier
                .size(136.dp)
                .graphicsLayer {
                    scaleX = coreScale
                    scaleY = coreScale
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, accent2))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = if (running) Color.Black else Color.White,
                modifier = Modifier.size(58.dp)
            )
        }
    }
}