/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.color.MaterialColors

/** ピンの直径 (dp). */
private const val PIN_DP = 36

/** ピンの縁の太さ (dp). */
private const val BORDER_DP = 3

/** ピンの中の, 数の文字の大きさの, 直径に対する割合. */
private const val TEXT_RATIO = 0.42f

/** 半分にするための値. */
private const val HALF = 2f

/**
 * 地図のピンの絵を作る (テーマの色の円に, 同じ場所の写真の枚数を書く).
 *
 * @receiver コンテキスト (テーマの色を使う)
 * @param count 写真の枚数
 * @return ピンの絵
 */
fun Context.pinIcon(count: Int): Drawable {
    val size = dp(PIN_DP)
    val border = dp(BORDER_DP).toFloat()
    val center = size / HALF
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0)
    canvas.drawCircle(center, center, center, paint)
    paint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0)
    canvas.drawCircle(center, center, center - border, paint)
    paint.apply {
        color =
            MaterialColors.getColor(
                this@pinIcon,
                com.google.android.material.R.attr.colorOnPrimary,
                0,
            )
        textSize = size * TEXT_RATIO
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    // 文字の高さの中央を, 円の中央に合わせる
    val baseline = center - (paint.descent() + paint.ascent()) / HALF
    canvas.drawText(count.toString(), center, baseline, paint)
    return bitmap.toDrawable(resources)
}
