/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 写真 (JPEG) を, 長いほうの辺が [maxPx] 程度になるように, 縮小して読み込む (大きな写真で, メモリを使い切らないため).
 *
 * @param jpeg 写真の JPEG
 * @param maxPx 長いほうの辺の目安 (px)
 * @return 画像. 読み込めないときは, null
 */
fun decodePhoto(
    jpeg: ByteArray,
    maxPx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
}
