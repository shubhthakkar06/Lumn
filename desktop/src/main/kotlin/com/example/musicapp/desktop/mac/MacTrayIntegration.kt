package com.example.musicapp.desktop.mac

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.Graphics2D
import java.awt.Color

object MacTrayIntegration {

    private var trayIcon: TrayIcon? = null

    fun setupTray(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrev: () -> Unit,
        onShowWindow: () -> Unit,
        onQuit: () -> Unit
    ) {
        if (!SystemTray.isSupported()) return

        try {
            val systemTray = SystemTray.getSystemTray()
            val popup = PopupMenu()

            val nowPlayingItem = MenuItem("Rivara Music").apply { isEnabled = false }
            val playPauseItem = MenuItem("Play / Pause").apply {
                addActionListener { onPlayPause() }
            }
            val nextItem = MenuItem("Next Track").apply {
                addActionListener { onNext() }
            }
            val prevItem = MenuItem("Previous Track").apply {
                addActionListener { onPrev() }
            }
            val showItem = MenuItem("Show Rivara").apply {
                addActionListener { onShowWindow() }
            }
            val quitItem = MenuItem("Quit").apply {
                addActionListener { onQuit() }
            }

            popup.add(nowPlayingItem)
            popup.addSeparator()
            popup.add(playPauseItem)
            popup.add(nextItem)
            popup.add(prevItem)
            popup.addSeparator()
            popup.add(showItem)
            popup.add(quitItem)

            val image = createTrayIconImage()
            trayIcon = TrayIcon(image, "Rivara Music", popup).apply {
                isImageAutoSize = true
                addActionListener { onShowWindow() }
            }

            systemTray.add(trayIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateNowPlaying(title: String, artist: String, isPlaying: Boolean) {
        trayIcon?.apply {
            toolTip = "Rivara: $title - $artist"
            popupMenu?.getItem(0)?.label = "$title • $artist"
            popupMenu?.getItem(2)?.label = if (isPlaying) "Pause" else "Play"
        }
    }

    private fun createTrayIconImage(): Image {
        val size = 22
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.color = Color(0x1E, 0xD7, 0x60)
        g.fillOval(3, 3, size - 6, size - 6)
        g.dispose()
        return image
    }
}
