package com.example.musicapp.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

class NowPlayingComplicationService : ComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("Rivara").build(),
            contentDescription = PlainComplicationText.Builder("Rivara Music").build()
        ).setTitle(PlainComplicationText.Builder("Now Playing").build()).build()
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val data = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("Rivara").build(),
            contentDescription = PlainComplicationText.Builder("Rivara Music").build()
        ).setTitle(PlainComplicationText.Builder("Playing").build()).build()

        listener.onComplicationData(data)
    }
}
