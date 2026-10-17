/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import jp.gaianeko.core.PhotoSource
import jp.gaianeko.core.Sighting
import jp.gaianeko.core.SightingGroup
import jp.gaianeko.core.SightingRemover
import jp.gaianeko.core.SightingSource
import jp.gaianeko.core.boundsOf
import jp.gaianeko.core.groupByPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** 最初に表示する, 地図の拡大率 (目撃がないとき). */
private const val DEFAULT_ZOOM = 5.0

/** 目撃が 1 つのとき (範囲の幅が 0 のとき) などの, 最大の拡大率 (最大にすると, タイルが無い拡大率になる). */
private const val MAX_ZOOM = 16.0

/** 全ての目撃が入る範囲に合わせるときの, 余白 (px). */
private const val BOUNDS_PADDING_PX = 100

/** 余白 (dp). */
private const val GAP_DP = 8

/**
 * 管理者の地図の画面 (全ての目撃を, ピンで表示する. ピンを押すと, 写真を取得して表示する).
 *
 * 地図は, OpenStreetMap (osmdroid). 位置と写真は, 管理者のロールの権限で読む (利用者は, 読めない).
 *
 * @property context コンテキスト (画面)
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @property sightings 目撃の読み出し
 * @property photos 写真の読み出し
 * @property remover 目撃と写真の削除 (写真を長押しして, 削除する)
 * @param onClose 閉じるを押したとき
 */
class AdminMapScreen(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sightings: SightingSource,
    private val photos: PhotoSource,
    private val remover: SightingRemover,
    onClose: () -> Unit,
) {
    private val map: MapView
    private val status = context.badgeText()
    private var total = 0

    /** 画面をまとめた View. */
    val view = FrameLayout(context)

    init {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        map =
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(DEFAULT_ZOOM)
            }
        view.addView(map, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.addView(
            context.materialButton(R.string.close, iconId = R.drawable.ic_close) { onClose() },
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply { setMargins(0, 0, context.dp(GAP_DP), 0) },
        )
        view.addView(
            status,
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ).apply {
                    setMargins(
                        context.dp(GAP_DP),
                        context.dp(GAP_DP),
                        context.dp(GAP_DP),
                        context.dp(GAP_DP),
                    )
                },
        )
    }

    /** 画面が前面に出たとき (地図を再開する). */
    fun onResume() = map.onResume()

    /** 画面が背面に回ったとき (地図を止める). */
    fun onPause() = map.onPause()

    /** 画面を閉じるとき (地図の資源を解放する). */
    fun onDestroy() = map.onDetach()

    /** 目撃を読み出して, ピンを置く (失敗したら, メッセージを表示する). */
    fun load() {
        status.text = context.getString(R.string.loading)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { sightings.all() } }
                .onSuccess { showPins(it) }
                .onFailure { status.text = context.getString(R.string.error, it.message) }
        }
    }

    /**
     * 同じ場所の目撃を, 1 つのピンにして, 地図に置く (全てが見える位置に合わせる).
     *
     * @param all 目撃
     */
    private fun showPins(all: List<Sighting>) {
        total = all.size
        status.text = context.getString(R.string.sighting_count, total)
        groupByPlace(all).forEach { group -> addPin(group) }
        boundsOf(all.map { it.point })?.let { b ->
            val box = BoundingBox(b.maxLatitude, b.maxLongitude, b.minLatitude, b.minLongitude)
            map.post { map.zoomToBoundingBox(box, false, BOUNDS_PADDING_PX, MAX_ZOOM, null) }
        }
        map.invalidate()
    }

    /**
     * 同じ場所の目撃の, ピンを置く (ピンに, 写真の枚数を書く. タップすると, 写真を, 次々に見られる.
     * 全て削除すると, ピンを消す).
     *
     * @param group 同じ場所の目撃
     */
    private fun addPin(group: SightingGroup) {
        val sightings = group.sightings.toMutableList()
        val marker = Marker(map)
        marker.position = GeoPoint(group.point.latitude, group.point.longitude)
        marker.icon = context.pinIcon(sightings.size)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.setOnMarkerClickListener { _, _ ->
            SightingDialog(context, scope, photos, remover, sightings) {
                total--
                status.text = context.getString(R.string.sighting_count, total)
                if (sightings.isEmpty()) {
                    map.overlays.remove(marker)
                } else {
                    marker.icon = context.pinIcon(sightings.size)
                }
                map.invalidate()
            }.show()
            true
        }
        map.overlays.add(marker)
    }
}
