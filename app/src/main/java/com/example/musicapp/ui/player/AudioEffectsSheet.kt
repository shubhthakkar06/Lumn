package com.example.musicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.audio.OutputKind
import com.example.musicapp.audio.SoundMode
import com.example.musicapp.ui.main.MainScreenViewModel
import kotlin.math.roundToInt

/** Rivara Sound: the in-player sound engine (works on every phone and Bluetooth device). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSheet(
    viewModel: MainScreenViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.soundSettings.collectAsStateWithLifecycle()
    val output by viewModel.soundOutput.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val studio = settings.mode == SoundMode.STUDIO

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F141C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, Color(0xFF00D2FF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (output.kind) {
                            OutputKind.HEADPHONES, OutputKind.EARBUDS -> Icons.Default.Headphones
                            OutputKind.CAR -> Icons.Default.DirectionsCar
                            OutputKind.EXTERNAL_SPEAKER -> Icons.Default.Speaker
                            OutputKind.SPEAKER -> Icons.Default.PhoneAndroid
                        },
                        contentDescription = null, tint = Color.Black
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rivara Sound", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(
                        if (settings.adaptive && !studio) "Tuned for ${output.name} (${output.kind.label.lowercase()})" else output.name,
                        color = Color(0xFF94A3B8), fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8)) }
            }

            Spacer(Modifier.height(18.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(SoundMode.entries.toList()) { mode ->
                    val selected = settings.mode == mode
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) accent else Color(0xFF1E293B))
                            .border(1.dp, if (selected) Color.Transparent else Color(0xFF334155), RoundedCornerShape(22.dp))
                            .clickable { viewModel.selectSoundMode(mode) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(mode.label, color = if (selected) Color.Black else Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(settings.mode.blurb, color = Color(0xFF94A3B8), fontSize = 13.sp)

            Spacer(Modifier.height(18.dp))
            SoundSlider("3D Space", "Room and hall around the music", settings.space, 0f..1f, percent = true, enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(space = v) }
            }
            SoundSlider(
                "Surround", if (output.kind == OutputKind.HEADPHONES || output.kind == OutputKind.EARBUDS) "Moves sound out of your head, in front of you" else "Headphones only",
                settings.surround, 0f..1f, percent = true, enabled = !studio, accent = accent
            ) { v -> viewModel.tweakSound { it.copy(surround = v) } }
            SoundSlider("Width", "Stereo spread (bass stays centred)", settings.width, 0f..1f, percent = true, enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(width = v) }
            }
            SoundSlider("Bass", "Low-end weight", settings.bassDb, -6f..12f, unit = "dB", enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(bassDb = v) }
            }
            SoundSlider("Vocals", "Presence around 3 kHz", settings.presenceDb, -6f..8f, unit = "dB", enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(presenceDb = v) }
            }
            SoundSlider("Treble", "Air and detail", settings.trebleDb, -6f..10f, unit = "dB", enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(trebleDb = v) }
            }
            SoundSlider("Punch", "Glue compression for impact", settings.punch, 0f..1f, percent = true, enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(punch = v) }
            }
            SoundSlider("Loudness", "Extra volume, limited so it never distorts", settings.loudnessDb, 0f..8f, unit = "dB", enabled = !studio, accent = accent) { v ->
                viewModel.tweakSound { it.copy(loudnessDb = v) }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF161F2E)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Adapt to device", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Safer bass on the phone speaker, crossfeed only on headphones, clearer vocals in the car", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Switch(checked = settings.adaptive, enabled = !studio, onCheckedChange = { viewModel.setSoundAdaptive(it) })
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { viewModel.selectSoundMode(SoundMode.CINEMATIC) }) {
                Text("Reset to Cinematic 3D", color = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun SoundSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "",
    percent: Boolean = false,
    enabled: Boolean,
    accent: Color,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) Color.White else Color(0xFF64748B), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Text(
                when {
                    percent -> "${(value * 100).roundToInt()}%"
                    else -> (if (value > 0.05f) "+" else "") + "%.1f %s".format(value, unit)
                },
                color = if (enabled) accent else Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF243247))
        )
    }
}

