/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlin.math.abs

/** 横にスワイプしたと判定する, 最小の速さ (px/s). */
private const val SWIPE_MIN_VELOCITY = 300f

/** 写真の角の丸み (dp). */
private const val CORNER_DP = 16

/**
 * 左右のスワイプと, 長押しを, 受け取る画像 (写真を, 次々に見る. 長押しで, 削除する. 角を丸くする).
 *
 * コードからだけ作る (レイアウトの XML には書かない) ので, XML 用のコンストラクタは, 作らない.
 *
 * @param context コンテキスト
 * @param onSwipe 左にスワイプしたとき 1 (次), 右にスワイプしたとき -1 (前) で呼ぶ
 * @param onLongPress 長押ししたとき
 */
@SuppressLint("ViewConstructor")
class SwipeImageView(
    context: Context,
    onSwipe: (Int) -> Unit,
    onLongPress: () -> Unit,
) : ShapeableImageView(context) {
    private val detector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    val horizontal =
                        abs(velocityX) > abs(velocityY) && abs(velocityX) > SWIPE_MIN_VELOCITY
                    if (horizontal) {
                        onSwipe(if (velocityX < 0) 1 else -1)
                    }
                    return horizontal
                }

                override fun onLongPress(e: MotionEvent) = onLongPress()
            },
        )

    init {
        adjustViewBounds = true
        scaleType = ScaleType.FIT_CENTER
        shapeAppearanceModel =
            ShapeAppearanceModel
                .builder()
                .setAllCorners(CornerFamily.ROUNDED, context.dp(CORNER_DP).toFloat())
                .build()
    }

    /**
     * タッチを, 受け取る (スワイプと, 長押しの判定に渡す).
     *
     * @param event タッチの動き
     * @return 受け取ったときは true
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled
    }

    /**
     * 画像を押したとき (アクセシビリティのために, 上書きする. 何もしない).
     *
     * @return 親の処理の結果
     */
    override fun performClick(): Boolean = super.performClick()
}
