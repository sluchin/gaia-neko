/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.view.isVisible

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/**
 * お気に入りの画面 (里親募集のカードの, ハートのボタンで足した猫を, 同じカードで並べる).
 *
 * お気に入りが変わったら ([Favorites.addListener]), すぐに並べ直す. ここでハートを押すと, 外れる.
 *
 * @param context コンテキスト (画面)
 * @param images 写真の読み込み
 * @property favorites お気に入り
 */
class FavoritesScreen(
    context: Context,
    images: ImageLoader,
    private val favorites: Favorites,
) {
    private val count =
        context.styledText(
            com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
    private val empty = context.cardText().apply { setText(R.string.favorites_empty) }
    private val grid = context.catGrid(favorites.cats, images, favorites)
    private val title =
        context
            .styledText(
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
                androidx.appcompat.R.attr.colorPrimary,
            ).apply { setText(R.string.favorites_title) }

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        title,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(count)
                },
            )
            addSpaced(empty, GAP_DP)
            addView(
                grid.first,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = context.dp(GAP_DP)
                },
            )
        }

    init {
        favorites.addListener { show() }
        show()
    }

    /** 匹数と, お気に入りがないときの案内を表示して, 並べ直す. */
    private fun show() {
        val size = favorites.cats.size
        count.text = view.resources.getQuantityString(R.plurals.adoption_count, size, size)
        empty.isVisible = size == 0
        grid.second.notifyDataSetChanged()
    }
}
