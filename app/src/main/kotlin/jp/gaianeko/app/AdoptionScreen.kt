/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import jp.gaianeko.core.ADOPTION_URL
import jp.gaianeko.core.AdoptionCat
import jp.gaianeko.core.parseAdoptionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一覧の列の数. */
private const val COLUMNS = 2

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/** カードとカードの間の余白 (dp). */
private const val CARD_GAP_DP = 8

/**
 * 里親募集の画面 (ニャン友ねっとわーく北海道の, 里親募集の一覧を, カードで並べる).
 *
 * 一覧は, 開いたときに, サイトから取得する (保存しない). 写真は, [ImageLoader] で, サイトから取得する.
 * カードを押すと, サイトの詳しいページを, ブラウザで開く.
 *
 * @property context コンテキスト (画面)
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @property images 写真の読み込み
 */
class AdoptionScreen(
    private val context: Context,
    private val scope: CoroutineScope,
    private val images: ImageLoader,
) {
    private val count =
        context.styledText(
            com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
    private val reload =
        context.materialButton(
            R.string.reload,
            androidx.appcompat.R.attr.borderlessButtonStyle,
        ) { load() }
    private val busy = context.busyIndicator()
    private val message = context.cardText()
    private val grid =
        GridView(context).apply {
            numColumns = COLUMNS
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = context.dp(CARD_GAP_DP)
            verticalSpacing = context.dp(CARD_GAP_DP)
            clipToPadding = false
        }
    private var loaded = false

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(busy)
            addView(header())
            addView(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ).apply { setText(R.string.adoption_source) },
            )
            addSpaced(message, GAP_DP)
            addView(
                grid,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = context.dp(GAP_DP)
                },
            )
        }

    /** 初めて表示するときだけ, 一覧を取得する (2 回目からは, 再読み込みのボタンで). */
    fun loadOnce() {
        if (!loaded) {
            load()
        }
    }

    /** 一覧を, サイトから取得して, 表示する (失敗したら, メッセージを表示する). */
    private fun load() {
        busy.isVisible = true
        reload.isEnabled = false
        message.showOrHide("")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    parseAdoptionList(String(fetchBytes(ADOPTION_URL), Charsets.UTF_8))
                }
            }.onSuccess {
                loaded = true
                show(it)
            }.onFailure { message.showOrHide(context.getString(R.string.error, it.message)) }
            busy.isVisible = false
            reload.isEnabled = true
        }
    }

    /**
     * 猫を, カードで並べる.
     *
     * @param cats 猫
     */
    private fun show(cats: List<AdoptionCat>) {
        count.text =
            context.resources.getQuantityString(R.plurals.adoption_count, cats.size, cats.size)
        if (cats.isEmpty()) {
            message.showOrHide(context.getString(R.string.adoption_empty))
        }
        grid.adapter = CatAdapter(cats)
        grid.setOnItemClickListener { _, _, position, _ ->
            context.startActivity(Intent(Intent.ACTION_VIEW, cats[position].url.toUri()))
        }
    }

    /**
     * 見出し (題名, 匹数, 再読み込みのボタン) を作る.
     *
     * @return 見出し
     */
    private fun header(): LinearLayout =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.adoption_title) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(count)
            addView(reload)
        }

    /**
     * 猫のカードを並べる, 一覧のアダプタ.
     *
     * @param cats 猫
     */
    private inner class CatAdapter(
        private val cats: List<AdoptionCat>,
    ) : ArrayAdapter<AdoptionCat>(context, R.layout.item_adoption, cats) {
        /**
         * 1 枚のカードを作る (使い回せるカードがあれば, 使い回す).
         *
         * @param position 何番目か
         * @param convertView 使い回せるカード
         * @param parent 一覧
         * @return カード
         */
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val card =
                convertView
                    ?: LayoutInflater.from(context).inflate(R.layout.item_adoption, parent, false)
            val cat = cats[position]
            card.findViewById<TextView>(R.id.name).text = cat.name
            card.findViewById<TextView>(R.id.details).showOrHide(
                listOfNotNull(cat.age, cat.sex).joinToString(" · "),
            )
            card.findViewById<TextView>(R.id.status).showOrHide(cat.status.orEmpty())
            images.load(card.findViewById<ImageView>(R.id.photo), cat.imageUrl)
            return card
        }
    }
}
