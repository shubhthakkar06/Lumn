package com.example.musicapp.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

object PaletteHelper {
    private val client = OkHttpClient()
    private val paletteCache = ConcurrentHashMap<String, List<Color>>()

    suspend fun extractColorsFromUrl(imageUrl: String): List<Color> = withContext(Dispatchers.IO) {
        if (imageUrl.isBlank()) return@withContext defaultGradient()
        paletteCache[imageUrl]?.let { return@withContext it }

        try {
            val request = Request.Builder()
                .url(imageUrl)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val bytes = response.body!!.bytes()
                // Decode scaled-down thumbnail to save memory and speed up palette generation
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val darkVibrant = palette.getDarkVibrantColor(0)
                    val vibrant = palette.getVibrantColor(0)
                    val dominant = palette.getDominantColor(0)
                    val darkMuted = palette.getDarkMutedColor(0)

                    val primaryHex = when {
                        darkVibrant != 0 -> darkVibrant
                        vibrant != 0 -> vibrant
                        darkMuted != 0 -> darkMuted
                        dominant != 0 -> dominant
                        else -> 0xFF233E4D.toInt()
                    }

                    val baseColor = Color(primaryHex)
                    val topColor = baseColor.copy(alpha = 0.85f)
                    val midColor = Color(
                        red = (baseColor.red * 0.35f).coerceIn(0f, 1f),
                        green = (baseColor.green * 0.35f).coerceIn(0f, 1f),
                        blue = (baseColor.blue * 0.35f).coerceIn(0f, 1f),
                        alpha = 1f
                    )
                    val bottomColor = Color(0xFF121212)

                    val colors = listOf(topColor, midColor, bottomColor)
                    paletteCache[imageUrl] = colors
                    return@withContext colors
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext defaultGradient()
    }

    fun defaultGradient(): List<Color> = listOf(
        Color(0xFF233E4D),
        Color(0xFF142028),
        Color(0xFF121212)
    )
}

@Composable
fun rememberDynamicGradient(imageUrl: String): List<Color> {
    var colors by remember(imageUrl) { mutableStateOf(PaletteHelper.defaultGradient()) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotBlank()) {
            val extracted = PaletteHelper.extractColorsFromUrl(imageUrl)
            colors = extracted
        }
    }
    return colors
}
