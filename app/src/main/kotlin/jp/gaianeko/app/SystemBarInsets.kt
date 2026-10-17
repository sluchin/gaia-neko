/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.ScrollView

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/**
 * 内容が, システムバー (ステータスバーなど) の下に, 隠れないようにする.
 *
 * Android 15 以降 (targetSdk 35 以上) は, 画面が, システムバーの下まで広がる (edge-to-edge).
 * システムバーの余白を, パディングに入れる. それ以前の版は, 広がらないので, 何もしない.
 * アクションバーは, AppCompat が, 内容の上に置くので, その高さは, 入れない.
 *
 * @receiver 画面の最も外側の View
 */
fun View.applySystemBarInsets() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }
    val padding = context.dp(CONTENT_PADDING_DP)
    setOnApplyWindowInsetsListener { target, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        target.setPadding(
            bars.left + padding,
            bars.top + padding,
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
