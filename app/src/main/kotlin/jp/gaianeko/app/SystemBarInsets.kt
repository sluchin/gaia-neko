/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.ScrollView

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/**
 * 内容が, システムバー (ステータスバーなど) と, アクションバーの下に, 隠れないようにする.
 *
 * Android 15 以降 (targetSdk 35 以上) は, 画面が, システムバーの下まで広がる (edge-to-edge).
 * システムバーの余白と, アクションバーの高さを, パディングに入れる. それ以前の版は, 広がらないので,
 * 何もしない.
 *
 * @receiver 画面の最も外側の View
 */
fun View.applySystemBarInsets() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }
    val padding = context.dp(CONTENT_PADDING_DP)
    val actionBarSize =
        TypedValue().let {
            context.theme.resolveAttribute(android.R.attr.actionBarSize, it, true)
            TypedValue.complexToDimensionPixelSize(it.data, resources.displayMetrics)
        }
    setOnApplyWindowInsetsListener { target, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        target.setPadding(
            bars.left + padding,
            bars.top + actionBarSize + padding,
            bars.right + padding,
            bars.bottom + padding,
        )
        insets
    }
}

/**
 * 画面の中身を差し替える.
 *
 * @receiver 画面の最も外側の View
 * @param content 表示する View
 */
fun ScrollView.showContent(content: View) {
    removeAllViews()
    addView(content)
}
