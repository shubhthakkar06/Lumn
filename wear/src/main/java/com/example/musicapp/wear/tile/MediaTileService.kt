package com.example.musicapp.wear.tile

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MediaTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val clickIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("com.example.musicapp.wear.MainActivity")
                    .build()
            )
            .build()

        val text = LayoutElementBuilders.Text.Builder()
            .setText("Rivara Playing")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(14f))
                    .setColor(ColorBuilders.argb(0xFF1ED760.toInt()))
                    .build()
            )
            .build()

        val title = LayoutElementBuilders.Text.Builder()
            .setText("Tap to Open Music")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(12f))
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .addContent(text)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
            .addContent(title)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_app")
                            .setOnClick(clickIntent)
                            .build()
                    )
                    .build()
            )
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(column).build()

        val timeline = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(
                androidx.wear.tiles.TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        androidx.wear.tiles.TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(timeline)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion("1").build()
        )
    }
}
