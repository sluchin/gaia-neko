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
 * ログインしたあとの画面の, 下のタブ (猫情報提供 (撮影) と, 里親募集と, お気に入り).
 *
 * 猫情報提供のタブは, スクロールする. 里親募集とお気に入りのタブは, 一覧が自分でスクロールするので,
 * そのまま置く. 里親募集の一覧は, 初めてタブを開いたときに, 取得する. お気に入りは, 初めてタブを開いたときに,
 * 募集が終わったかを, サイトで確かめる.
 *
 * @param context コンテキスト
 * @param home 猫情報提供 (撮影) のタブの中身
 * @param adoption 里親募集の画面 (画面を作り直しても, 取得した一覧を使い回す)
 * @param favorites お気に入り (タブを開いたときに, 募集が終わったかを確かめる)
 * @param favoritesView お気に入りの画面 (画面を作り直しても, 使い回す)
 */
class HomeTabs(
    context: Context,
    home: View,
    adoption: AdoptionScreen,
    favorites: Favorites,
    favoritesView: View,
) {
    /** タブごとの中身 (下のタブの項目の ID から). */
    private val tabs =
        mapOf(
            R.id.nav_capture to ScrollView(context).apply { addView(home) },
            R.id.nav_adoption to adoption.view,
            R.id.nav_favorites to favoritesView,
        )

    /** 画面をまとめた View. */
    val view: LinearLayout

    init {
        val padding = context.dp(CONTENT_PADDING_DP)
        val content =
            FrameLayout(context).apply {
                setPadding(padding, padding, padding, 0)
                tabs.forEach { (id, tab) ->
                    // 前の画面 (地図から戻る前) に付いていれば, 外す
                    (tab.parent as? ViewGroup)?.removeView(tab)
                    tab.isVisible = id == R.id.nav_capture
                    addView(tab)
                }
            }
        val nav =
            BottomNavigationView(context).apply {
                inflateMenu(R.menu.bottom_nav)
                setOnItemSelectedListener { item ->
                    tabs.forEach { (id, tab) -> tab.isVisible = id == item.itemId }
                    when (item.itemId) {
                        R.id.nav_adoption -> adoption.loadOnce()
                        R.id.nav_favorites -> favorites.checkOnce()
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
