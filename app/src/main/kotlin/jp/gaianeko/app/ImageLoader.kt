/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

/** 同時に取得する画像の数 (サイトに負荷をかけない). */
private const val THREADS = 2

/** メモリに残す画像の量の, 使えるメモリに対する割合の分母. */
private const val MEMORY_FRACTION = 8

/**
 * 画像 (サイトの URL) を読み込んで, 表示する (里親募集の猫の写真).
 *
 * 画像は, リポジトリにも, アプリにも, 含めない. 実行のときに, サイトの URL から取得して, 端末の
 * キャッシュ (メモリと, ファイル) に残す. 2 回目からは, 通信しない. 取得できなければ, 代わりの絵のまま.
 *
 * @param context コンテキスト
 */
class ImageLoader(
    context: Context,
) {
    private val dir = File(context.cacheDir, "adoption-images").apply { mkdirs() }
    private val executor = Executors.newFixedThreadPool(THREADS)
    private val memory =
        object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / MEMORY_FRACTION).toInt(),
        ) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    /**
     * 画像を表示する (取得できるまでは, 代わりの絵).
     *
     * @param view 表示する部品 (一覧の部品は, 使い回されるので, 取得が終わったときに, 同じ URL のときだけ表示する)
     * @param url 画像の URL (なければ, 代わりの絵のまま)
     */
    fun load(
        view: ImageView,
        url: String?,
    ) {
        view.tag = url
        view.setImageResource(R.drawable.photo_placeholder)
        val cached = url?.let { memory.get(it) }
        if (cached != null) {
            view.setImageBitmap(cached)
        } else if (url != null) {
            executor.execute {
                val bitmap = fromFile(url) ?: download(url)
                if (bitmap != null) {
                    memory.put(url, bitmap)
                    view.post { if (view.tag == url) view.setImageBitmap(bitmap) }
                }
            }
        }
    }

    /** 取得を止める (画面を閉じるとき). */
    fun shutdown() = executor.shutdown()

    /**
     * 端末のキャッシュから, 画像を読む.
     *
     * @param url 画像の URL
     * @return 画像. なければ, null
     */
    private fun fromFile(url: String): Bitmap? =
        file(url)
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.path) }

    /**
     * サイトから, 画像を取得して, 端末のキャッシュに残す.
     *
     * @param url 画像の URL
     * @return 画像. 取得できなければ, null (エラーは, 画面に出さない. 代わりの絵のままにする)
     */
    @Suppress("SwallowedException")
    private fun download(url: String): Bitmap? =
        try {
            val bytes = fetchBytes(url)
            file(url).writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: IOException) {
            null
        }

    /**
     * 画像の, 端末のキャッシュのファイル (URL の SHA-256 の名前).
     *
     * @param url 画像の URL
     * @return ファイル
     */
    private fun file(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) })
    }
}
