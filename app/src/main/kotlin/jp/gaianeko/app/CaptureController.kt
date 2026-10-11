/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import jp.gaianeko.core.GeoPoint
import jp.gaianeko.core.SightingUploader
import jp.gaianeko.core.geoPointOrNull
import jp.gaianeko.core.parseExifGps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 位置の権限を求めるときの, 要求の番号. */
private const val REQUEST_LOCATION = 1

/** カメラを起動するときの, 要求の番号. */
private const val REQUEST_CAMERA = 2

/** 撮影した写真を置く, キャッシュの中のディレクトリ (file_paths.xml と同じ). */
private const val IMAGES_DIR = "images"

/** 撮影した写真のファイル名. */
private const val CAPTURE_FILE = "capture.jpg"

/** ミリ秒を, 秒にするための値. */
private const val MILLIS_PER_SECOND = 1000L

/**
 * 撮影して, 保存するまでの流れ (位置の権限, カメラの起動, 位置の取得, 保存).
 *
 * 位置は, 写真の Exif の GPS を使う. なければ, 端末の最後の位置を使う. どちらもなければ, 保存しない
 * (位置のない目撃は, 管理者が, マップに出せないため).
 *
 * @property activity 画面
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @property uploader 目撃の保存
 * @property onBusy 通信を始める (true), 終わる (false) とき
 * @property onMessage 結果のメッセージを表示するとき
 */
class CaptureController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val uploader: SightingUploader,
    private val onBusy: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val file = File(File(activity.cacheDir, IMAGES_DIR), CAPTURE_FILE)

    /** 撮影を始める (位置の権限がなければ, 先に求める). */
    fun start() {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                REQUEST_LOCATION,
            )
        }
    }

    /**
     * 位置の権限の結果を受け取る (許可されたら, カメラを起動する).
     *
     * @param requestCode 要求の番号
     * @param grantResults 権限ごとの結果
     */
    fun onPermissionResult(
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode != REQUEST_LOCATION) {
            return
        }
        if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
            launchCamera()
        } else {
            onMessage(activity.getString(R.string.location_denied))
        }
    }

    /**
     * カメラの結果を受け取る (撮影できていれば, 位置を求めて, 保存する).
     *
     * @param requestCode 要求の番号
     * @param resultCode 結果 (撮影できたときは, RESULT_OK)
     */
    fun onCameraResult(
        requestCode: Int,
        resultCode: Int,
    ) {
        if (requestCode != REQUEST_CAMERA || resultCode != Activity.RESULT_OK) {
            return
        }
        onBusy(true)
        scope.launch {
            val message =
                runCatching { withContext(Dispatchers.IO) { save() } }
                    .fold(
                        { activity.getString(it) },
                        { activity.getString(R.string.error, it.message) },
                    )
            file.delete()
            onMessage(message)
            onBusy(false)
        }
    }

    /** カメラを起動する (撮影した写真は, アプリのキャッシュに書き込まれる). */
    private fun launchCamera() {
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent =
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        activity.startActivityForResult(intent, REQUEST_CAMERA)
    }

    /**
     * 撮影した写真と位置を, 保存する (IO のスレッドで実行する).
     *
     * @return 結果のメッセージの文字列のリソース
     */
    private suspend fun save(): Int {
        val point = findPoint() ?: return R.string.no_location
        uploader.upload(file.readBytes(), point, System.currentTimeMillis() / MILLIS_PER_SECOND)
        return R.string.saved
    }

    /**
     * 撮影した位置を求める (写真の Exif の GPS. なければ, 端末の最後の位置).
     *
     * @return 位置. 求められないときは, null
     */
    private fun findPoint(): GeoPoint? {
        val exif = ExifInterface(file.absolutePath)
        return parseExifGps(
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF),
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF),
        ) ?: lastKnownLocation()?.let { geoPointOrNull(it.latitude, it.longitude) }
    }

    /**
     * 端末の最後の位置を返す (GPS と, ネットワークのうち, 新しいほう).
     *
     * @return 位置. なければ, null
     */
    private fun lastKnownLocation(): Location? {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = activity.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }
}
