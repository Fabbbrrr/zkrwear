package com.zkrwatch.complication

import androidx.wear.watchface.complications.data.ColorRamp
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.zkrwatch.data.cache.StatusCache

/**
 * Pushes the Zkr SOC to a watch-face complication as SHORT_TEXT ("72%") and
 * RANGED_VALUE (0–100 arc). Reads the [StatusCache] only — the app / Tile keep it
 * fresh — so the complication never does network on its update path.
 */
class SocComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        buildData(type, soc = 72)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        buildData(request.complicationType, soc = StatusCache(this).read().socPercent)

    private fun buildData(type: ComplicationType, soc: Int?): ComplicationData {
        val label = soc?.let { "$it%" } ?: "--"
        val desc = PlainComplicationText.Builder("Battery").build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = (soc ?: 0).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = desc,
            )
                .setText(PlainComplicationText.Builder(label).build())
                // Zkr green arc (watch faces that honour the ramp will render it).
                .setColorRamp(ColorRamp(intArrayOf(0xFF2E7D32.toInt(), 0xFF4FD46A.toInt()), true))
                .build()

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = desc,
            ).build()
        }
    }
}
