package com.example.musicapp.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.musicapp.audio.DeviceCategory
import com.example.musicapp.audio.EqPresetMode
import com.example.musicapp.ui.main.MainScreenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSheet(
    viewModel: MainScreenViewModel,
    onDismiss: () -> Unit
) {
    val activeProfile by viewModel.activeAudioProfile.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F141C),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF00F59B), Color(0xFF00D2FF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (activeProfile?.category) {
                            DeviceCategory.EARBUDS -> Icons.Default.Headphones
                            DeviceCategory.HEADPHONES -> Icons.Default.Headphones
                            DeviceCategory.HOME_THEATRE -> Icons.Default.Speaker
                            DeviceCategory.CAR_AUDIO -> Icons.Default.DirectionsCar
                            else -> Icons.Default.PhoneAndroid
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Adaptive Audio Engine",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = activeProfile?.deviceName ?: "Detecting device...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00D2FF)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Active Profile Info Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1E293B), Color(0xFF172033))
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00F59B).copy(alpha = 0.3f), Color(0xFF00D2FF).copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00F59B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = activeProfile?.profileLabel ?: "Hardware DSP Active",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeProfile?.description ?: "Hardware equalizer tuned to your connected audio output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Presets Selector
            Text(
                text = "Acoustic Presets",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFE2E8F0)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(EqPresetMode.entries.toTypedArray()) { preset ->
                    val isSelected = activeProfile?.presetMode == preset
                    val bgBrush = if (isSelected) {
                        Brush.horizontalGradient(listOf(Color(0xFF00F59B), Color(0xFF00D2FF)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                    }
                    val textColor = if (isSelected) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(bgBrush)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else Color(0xFF334155),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                viewModel.setAudioPreset(preset)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = preset.displayName,
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Real-Time Audio Metric Gauges (BassBoost & 3D Spatial Field)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bass Metric Slider
                AudioMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Bass Boost",
                    strengthValue = activeProfile?.bassBoostStrength?.toInt() ?: 0,
                    maxValue = 1000,
                    accentColor = Color(0xFFFF5757),
                    enabled = activeProfile?.bassBoostAvailable == true || activeProfile?.equalizerAvailable == true,
                    onValueChange = { newValue ->
                        viewModel.setBassBoostStrength(newValue.toShort())
                    }
                )

                // Spatial Virtualizer Metric Slider
                AudioMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "3D Virtualizer",
                    strengthValue = activeProfile?.virtualizerStrength?.toInt() ?: 0,
                    maxValue = 1000,
                    accentColor = Color(0xFF00D2FF),
                    enabled = activeProfile?.virtualizerAvailable == true,
                    onValueChange = { newValue ->
                        viewModel.setVirtualizerStrength(newValue.toShort())
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Loudness Enhancer Gain Card
            var loudnessGain by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161F2E))
                    .border(1.dp, Color(0xFF243247), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Loudness Boost (Hardware DSP Gain)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "+${loudnessGain.toInt()} mB",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF00F59B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = loudnessGain,
                        onValueChange = {
                            loudnessGain = it
                            viewModel.setLoudnessGain(it.toInt())
                        },
                        valueRange = 0f..1200f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00F59B),
                            activeTrackColor = Color(0xFF00F59B),
                            inactiveTrackColor = Color(0xFF243247)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    strengthValue: Int,
    maxValue: Int,
    accentColor: Color,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val percent = ((strengthValue.toFloat() / maxValue.toFloat()) * 100).toInt()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161F2E))
            .border(1.dp, Color(0xFF243247), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = strengthValue.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..maxValue.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color(0xFF243247)
                )
            )
        }
    }
}
