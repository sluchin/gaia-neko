/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import jp.gaianeko.core.PhotoSource
import jp.gaianeko.core.Sighting
import jp.gaianeko.core.SightingSource
import jp.gaianeko.core.boundsOf
import jp.gaianeko.core.formatTakenAt
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
import java.time.ZoneId

/** 最初に表示する, 地図の拡大率 (目撃がないとき). */
private const val DEFAULT_ZOOM = 5.0

/** 全ての目撃が入る範囲に合わせるときの, 余白 (px). */
private const val BOUNDS_PADDING_PX = 100

/** 写真の, 表示する大きさの目安 (px). */
private const val PHOTO_MAX_PX = 1024

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
 * @param onClose 閉じるを押したとき
 */
class AdminMapScreen(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sightings: SightingSource,
    private val photos: PhotoSource,
    onClose: () -> Unit,
) {
    private val map: MapView
    private val status = TextView(context)

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
            Button(context).apply {
                setText(R.string.close)
                setOnClickListener { onClose() }
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ),
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
     * 目撃のピンを, 地図に置いて, 全てが見える位置に合わせる.
     *
     * @param all 目撃
     */
    private fun showPins(all: List<Sighting>) {
        status.text = context.getString(R.string.sighting_count, all.size)
        all.forEach { sighting ->
            map.overlays.add(
                Marker(map).apply {
                    position = GeoPoint(sighting.point.latitude, sighting.point.longitude)
                    setOnMarkerClickListener { _, _ ->
                        showPhoto(sighting)
                        true
                    }
                },
            )
        }
        boundsOf(all.map { it.point })?.let { b ->
            val box = BoundingBox(b.maxLatitude, b.maxLongitude, b.minLatitude, b.minLongitude)
            map.post { map.zoomToBoundingBox(box, false, BOUNDS_PADDING_PX) }
        }
        map.invalidate()
    }

    /**
     * ピンの写真を取得して, 撮影した時刻と一緒に, ダイアログで表示する.
     *
     * @param sighting 目撃
     */
    private fun showPhoto(sighting: Sighting) {
        status.text = context.getString(R.string.loading)
        scope.launch {
            val result =
                runCatching { withContext(Dispatchers.IO) { photos.get(sighting.photoKey) } }
            status.text = context.getString(R.string.sighting_count, map.overlays.size)
            result
                .onSuccess { show(sighting, it) }
                .onFailure { status.text = context.getString(R.string.error, it.message) }
        }
    }

    /**
     * 写真と, 撮影した時刻を, ダイアログで表示する.
     *
     * @param sighting 目撃
     * @param jpeg 写真の JPEG
     */
    private fun show(
        sighting: Sighting,
        jpeg: ByteArray,
    ) {
        val image = ImageView(context).apply { setImageBitmap(decodePhoto(jpeg, PHOTO_MAX_PX)) }
        AlertDialog
            .Builder(context)
            .setTitle(formatTakenAt(sighting.takenAt, ZoneId.systemDefault()))
            .setView(image)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
