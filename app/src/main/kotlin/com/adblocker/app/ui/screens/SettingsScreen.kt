package com.adblocker.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adblocker.app.R
import com.adblocker.app.data.FilterCatalog
import com.adblocker.app.ui.components.DedicationCard
import com.adblocker.app.ui.components.GlassCard
import com.adblocker.app.ui.components.Reveal
import com.adblocker.app.ui.components.SectionTitle
import com.adblocker.app.ui.components.ShimmerBar
import com.adblocker.app.ui.components.pressScale
import com.adblocker.app.ui.theme.LocalPalette
import com.adblocker.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val p = LocalPalette.current
    val selected by vm.selected.collectAsState()
    val dark by vm.darkMode.collectAsState()
    val autoStart by vm.autoStart.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Reveal(0) {
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.text)
        }

        Reveal(1) {
            GlassCard(Modifier.fillMaxWidth(), glow = p.accent) {
                SectionTitle(R.drawable.ic_shield, "Protection level", p.accent)
                FilterCatalog.levels.forEach { level ->
                    LevelOption(
                        level = level,
                        selected = level.ids == selected,
                        onClick = { vm.setLevel(level.ids) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Reveal(2) {
            GlassCard(Modifier.fillMaxWidth(), glow = p.accent2) {
                SectionTitle(R.drawable.ic_moon, "Appearance", p.accent2)
                SettingRow(
                    title = "Night mode",
                    subtitle = if (dark) "Enabled" else "Day mode enabled",
                    checked = dark,
                    onChange = { vm.setDarkMode(it) }
                )
            }
        }

        Reveal(3) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(R.drawable.ic_gear, "General", p.accent)
                SettingRow(
                    title = "Auto-start",
                    subtitle = "Start protection after the phone boots",
                    checked = autoStart,
                    onChange = { vm.setAutoStart(it) }
                )
            }
        }

        Reveal(4) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.refreshLists() },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent2, contentColor = Color.Black)
                ) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Update protection", fontWeight = FontWeight.Bold)
                }
                if (busy) {
                    ShimmerBar(modifier = Modifier.fillMaxWidth().height(6.dp), color = p.accent2)
                }
                if (status.isNotEmpty()) {
                    Text(status, color = p.muted, fontSize = 12.sp)
                }
            }
        }

        Reveal(5) { DedicationCard() }

        Reveal(6) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionTitle(R.drawable.ic_info, "Tip", p.accent)
                Text(
                    "If you notice an ad still showing, try turning off \"Private DNS\" from your phone's network settings.",
                    color = p.muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("Ad Blocker • Version 1.1.0", color = p.muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LevelOption(
    level: FilterCatalog.Level,
    selected: Boolean,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        if (selected) p.accent else p.muted.copy(alpha = 0.25f),
        tween(300),
        label = "levelBorder"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(if (selected) p.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(level.name, color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(level.hint, color = p.muted, fontSize = 12.sp)
        }
        Icon(
            painter = painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_shield),
            contentDescription = null,
            tint = if (selected) p.accent else p.muted,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = p.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = p.accent,
                uncheckedTrackColor = p.muted.copy(alpha = 0.3f)
            )
        )
    }
}