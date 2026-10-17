/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/**
 * ログインしたあとの画面の, 下のタブ (撮影と, 里親募集).
 *
 * 撮影のタブは, スクロールする. 里親募集のタブは, 一覧が自分でスクロールするので, そのまま置く.
 * 里親募集の一覧は, 初めてタブを開いたときに, 取得する.
 *
 * @param context コンテキスト
 * @param home 撮影のタブの中身
 * @param adoption 里親募集の画面 (画面を作り直しても, 取得した一覧を使い回す)
 */
class HomeTabs(
    context: Context,
    home: View,
    adoption: AdoptionScreen,
) {
    private val homeTab = ScrollView(context).apply { addView(home) }
    private val adoptionTab = adoption.view

    /** 画面をまとめた View. */
    val view: LinearLayout

    init {
        // 前の画面 (地図から戻る前) に付いていれば, 外す
        (adoptionTab.parent as? ViewGroup)?.removeView(adoptionTab)
        adoptionTab.isVisible = false
        val padding = context.dp(CONTENT_PADDING_DP)
        val content =
            FrameLayout(context).apply {
                setPadding(padding, padding, padding, 0)
                addView(homeTab)
                addView(adoptionTab)
            }
        val nav =
            BottomNavigationView(context).apply {
                inflateMenu(R.menu.bottom_nav)
                setOnItemSelectedListener {
                    val showAdoption = it.itemId == R.id.nav_adoption
                    homeTab.isVisible = !showAdoption
                    adoptionTab.isVisible = showAdoption
                    if (showAdoption) {
                        adoption.loadOnce()
                    }
                    true
                }
            }
        view =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    content,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
                )
                addView(nav)
            }
        applyInsets()
    }

    /**
     * 左右と上に, システムバーの余白を入れる (下の余白は, 下のタブが, 自分で入れる).
     *
     * Android 15 以降 (targetSdk 35 以上) は, 画面が, システムバーの下まで広がる (edge-to-edge).
     */
    private fun applyInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        view.setOnApplyWindowInsetsListener { target, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
    }
}
