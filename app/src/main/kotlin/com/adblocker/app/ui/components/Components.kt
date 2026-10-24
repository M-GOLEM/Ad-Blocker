package com.adblocker.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.app.R
import com.adblocker.app.ui.theme.HeartPink
import com.adblocker.app.ui.theme.LocalPalette

/** Glass card with a glowing border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color = LocalPalette.current.accent,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(p.card))
            .border(
                1.dp,
                Brush.linearGradient(listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0.05f))),
                shape
            )
            .padding(16.dp),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
fun SectionTitle(icon: Int, title: String, color: Color) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = p.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(10.dp))
}

/** Number that animates smoothly from the old value to the new one. */
@Composable
fun AnimatedCounter(value: Long, color: Color, fontSize: TextUnit = 18.sp) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(900),
        label = "counter"
    )
    Text(
        String.format(java.util.Locale.US, "%,d", animated.toLong()),
        color = color,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize
    )
}

@Composable
fun StatTile(
    label: String,
    value: Long,
    icon: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    GlassCard(
        modifier = modifier,
        glow = color,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(8.dp))
        AnimatedCounter(value = value, color = p.text)
        Text(label, color = p.muted, fontSize = 11.sp)
    }
}

/** Staggered reveal: each element enters slightly after the previous one. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val delay = 120 + index * 110
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(550, delayMillis = delay)) +
            slideInVertically(tween(550, delayMillis = delay)) { it / 4 }
    ) {
        content()
    }
}

/** Press effect: the button shrinks slightly while pressed. */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "press"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Shimmering progress bar. */
@Composable
fun ShimmerBar(modifier: Modifier = Modifier, color: Color) {
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.15f), color, color.copy(alpha = 0.15f)),
                    start = Offset(t * 400f, 0f),
                    end = Offset(t * 400f + 300f, 0f)
                )
            )
    )
}

@Composable
fun DedicationCard() {
    val p = LocalPalette.current
    val beat by rememberInfiniteTransition(label = "beat").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "beatScale"
    )
    GlassCard(modifier = Modifier.fillMaxWidth(), glow = HeartPink) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                tint = HeartPink,
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        scaleX = beat
                        scaleY = beat
                    }
            )
            Column {
                Text("A special dedication to my mother", color = p.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Made with love by developer GOLEM", color = p.muted, fontSize = 13.sp)
            }
        }
    }
}