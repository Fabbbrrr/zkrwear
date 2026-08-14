package com.zkrwatch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zkrwatch.data.cache.StatusCache
import com.zkrwatch.presentation.MainActivity

/**
 * Glanceable Tile: Zkr SOC + range from the [StatusCache], tap to open the app.
 * Renders instantly from cache (no network on the tile path) and asks for a
 * refresh every 30 min.
 *
 * NOTE: background Lock/Unlock buttons directly on the tile are deferred to M5 —
 * they need a WorkManager command path (a tile clickable can enqueue a worker via
 * a LoadAction) so the action runs without opening the app. Until then the tile
 * is glance + tap-to-open.
 */
class ZkrTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val cached = StatusCache(this).read()
        val soc = cached.socPercent?.let { "$it%" } ?: "--"
        val range = cached.rangeKm?.let { "$it km" } ?: "range —"

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val white = argb(0xFFFFFFFF.toInt())
        val grey = argb(0xFF9AA0A6.toInt())
        val black = argb(0xFF000000.toInt())

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder().setColor(black).build(),
                    )
                    .setClickable(openApp)
                    .build(),
            )
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder(this, "ZkrWatch")
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2).setColor(grey).build(),
                    )
                    .addContent(
                        Text.Builder(this, soc)
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY1).setColor(white).build(),
                    )
                    .addContent(
                        Text.Builder(this, range)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(grey).build(),
                    )
                    .build(),
            )
            .build()

        val timeline = TimelineBuilders.Timeline.fromLayoutElement(root)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(REFRESH_MS)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val REFRESH_MS = 30L * 60L * 1000L
    }
}
