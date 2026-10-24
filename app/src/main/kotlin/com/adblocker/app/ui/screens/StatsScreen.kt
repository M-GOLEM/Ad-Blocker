package com.adblocker.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.app.R
import com.adblocker.app.ui.components.AnimatedCounter
import com.adblocker.app.ui.components.GlassCard
import com.adblocker.app.ui.components.Reveal
import com.adblocker.app.ui.theme.LocalPalette
import com.adblocker.app.viewmodel.MainViewModel

@Composable
fun StatsScreen(vm: MainViewModel) {
    val p = LocalPalette.current
    val blocked by vm.blocked.collectAsState()
    val allowed by vm.allowed.collectAsState()

    val total = blocked + allowed
    val rate = if (total > 0) blocked.toFloat() / total.toFloat() else 0f
    val animRate by animateFloatAsState(targetValue = rate, animationSpec = tween(1000), label = "rate")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(0) {
            Text("Protection statistics", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
        }

        Reveal(1) {
            GlassCard(Modifier.fillMaxWidth(), glow = p.danger, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 18.dp.toPx()
                        drawArc(
                            color = p.muted.copy(alpha = 0.15f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(p.accent, p.accent2, p.accent)),
                            startAngle = -90f,
                            sweepAngle = 360f * animRate,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%.0f%%".format(animRate * 100),
                            color = p.text,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "of ads were blocked",
                            color = p.muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Reveal(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.weight(1f), glow = p.danger, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_block), contentDescription = null, tint = p.danger, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    AnimatedCounter(value = blocked, color = p.text)
                    Text("Ads blocked", color = p.muted, fontSize = 11.sp)
                }
                GlassCard(Modifier.weight(1f), glow = p.accent, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = p.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(6.dp))
                    AnimatedCounter(value = allowed, color = p.text)
                    Text("Normal requests allowed", color = p.muted, fontSize = 11.sp)
                }
            }
        }

        Reveal(3) {
            Text(
                "Numbers reset when you close the app or press reset.",
                color = p.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Reveal(4) {
            Button(
                onClick = { vm.resetStats() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.Black)
            ) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset numbers", fontWeight = FontWeight.Bold)
            }
        }
    }
}