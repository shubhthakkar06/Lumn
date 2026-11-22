package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.musicapp.audio.OutputKind
import com.example.musicapp.audio.SoundMode
import com.example.musicapp.desktop.audio.DesktopSoundEngine
import kotlin.math.roundToInt

/** Rivara Sound on the Mac: same modes and controls as the phone. */
@Composable
fun DesktopEqualizerDialog(
    themeAccent: Color,
    onDismiss: () -> Unit
) {
    val settings by DesktopSoundEngine.settings.collectAsState()
    val output by DesktopSoundEngine.output.collectAsState()
    val studio = settings.mode == SoundMode.STUDIO
    val headphones = output.first == OutputKind.HEADPHONES || output.first == OutputKind.EARBUDS

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(560.dp).heightIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F141C)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SurroundSound, contentDescription = null, tint = themeAccent, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rivara Sound", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Output: ${output.second}", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8)) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundMode.entries.forEach { mode ->
                        val selected = settings.mode == mode
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (selected) themeAccent else Color(0xFF1E293B))
                                .border(1.dp, if (selected) Color.Transparent else Color(0xFF334155), RoundedCornerShape(18.dp))
                                .clickable { DesktopSoundEngine.selectMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(mode.label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(settings.mode.blurb, color = Color(0xFF94A3B8), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Control("3D Space", settings.space, 0f..1f, true, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(space = v) } }
                Control(if (headphones) "Surround (out-of-head)" else "Surround (headphones only)", settings.surround, 0f..1f, true, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(surround = v) } }
                Control("Width", settings.width, 0f..1f, true, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(width = v) } }
                Control("Bass", settings.bassDb, -6f..12f, false, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(bassDb = v) } }
                Control("Vocals", settings.presenceDb, -6f..8f, false, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(presenceDb = v) } }
                Control("Treble", settings.trebleDb, -6f..10f, false, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(trebleDb = v) } }
                Control("Punch", settings.punch, 0f..1f, true, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(punch = v) } }
                Control("Loudness", settings.loudnessDb, 0f..8f, false, !studio, themeAccent) { v -> DesktopSoundEngine.tweak { it.copy(loudnessDb = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Adapt to output device", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = settings.adaptive, enabled = !studio, onCheckedChange = { DesktopSoundEngine.update(settings.copy(adaptive = it)) })
                }
            }
        }
    }
}

@Composable
private fun Control(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    percent: Boolean,
    enabled: Boolean,
    accent: Color,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = if (enabled) Color.White else Color(0xFF64748B), modifier = Modifier.width(190.dp), fontSize = 14.sp)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent), modifier = Modifier.weight(1f)
        )
        Text(
            if (percent) "${(value * 100).roundToInt()}%" else "%+.1f dB".format(value),
            color = accent, fontSize = 13.sp, modifier = Modifier.width(70.dp)
        )
    }
}
