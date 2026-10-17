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
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import jp.gaianeko.core.ADOPTION_URL
import jp.gaianeko.core.AdoptionCat
import jp.gaianeko.core.AdoptionPage
import jp.gaianeko.core.parseAdoptionPage
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

/** 一覧の終わりから, この行数の手前まで見えたら, 次のページを取得する. */
private const val PREFETCH_ROWS = 2

/**
 * 里親募集の画面 (ニャン友ねっとわーく北海道の, 里親募集の一覧を, カードで並べる).
 *
 * 一覧は, 開いたときに, 最初のページを, サイトから取得する ([PageCache] で, 端末に残して, しばらくは
 * 取得し直さない). 一覧の終わりの近くまでスクロールしたら, 次のページを取得して, 後ろに足す
 * (全てのページを, 一度には取得しない). 再読み込みのボタンでは, 残したページを使わずに, 取得し直す.
 * 写真は, [ImageLoader] で, サイトから取得する. カードを押すと, サイトの詳しいページを, ブラウザで開く.
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
        ) { load(useCache = false) }
    private val busy = context.busyIndicator()
    private val message = context.cardText()
    private val pages = PageCache(context)
    private val cats = mutableListOf<AdoptionCat>()
    private val adapter = CatAdapter(cats)
    private val grid =
        GridView(context).apply {
            numColumns = COLUMNS
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = context.dp(CARD_GAP_DP)
            verticalSpacing = context.dp(CARD_GAP_DP)
            clipToPadding = false
            adapter = this@AdoptionScreen.adapter
            setOnItemClickListener { _, _, position, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, cats[position].url.toUri()))
            }
            setOnScrollListener(NearEnd())
        }
    private var loaded = false

    /** 次のページの URL (最後のページまで取得したか, 取得に失敗したら, null). */
    private var nextUrl: String? = null

    /** ページを取得している間は, true. */
    private var loading = false

    /** 端末に残したページを使うか (再読み込みのあとは, 次のページも, 取得し直す). */
    private var useCache = true

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
            load(useCache = true)
        }
    }

    /**
     * 一覧を, 最初のページから取得し直す.
     *
     * @param useCache 端末に残したページを使うか
     */
    private fun load(useCache: Boolean) {
        this.useCache = useCache
        cats.clear()
        adapter.notifyDataSetChanged()
        fetch(ADOPTION_URL)
    }

    /**
     * 1 ページを, サイトから取得して, 一覧の後ろに足す (失敗したら, メッセージを表示する).
     *
     * @param url ページの URL
     */
    private fun fetch(url: String) {
        loading = true
        nextUrl = null
        busy.isVisible = true
        reload.isEnabled = false
        message.showOrHide("")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    parseAdoptionPage(pages.get(url, useCache), url)
                }
            }.onSuccess {
                loaded = true
                show(it)
            }.onFailure { message.showOrHide(context.getString(R.string.error, it.message)) }
            loading = false
            busy.isVisible = false
            reload.isEnabled = true
        }
    }

    /**
     * 1 ページの猫を, 一覧の後ろに足す (ページの間で, 同じ猫が重なったら, 足さない).
     *
     * @param page ページ
     */
    private fun show(page: AdoptionPage) {
        val known = cats.map { it.url }.toSet()
        cats += page.cats.filter { it.url !in known }
        nextUrl = page.nextUrl
        adapter.notifyDataSetChanged()
        count.text =
            context.resources.getQuantityString(R.plurals.adoption_count, cats.size, cats.size)
        if (cats.isEmpty()) {
            message.showOrHide(context.getString(R.string.adoption_empty))
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

    /** 一覧の終わりの近くが見えたら, 次のページを取得する (取得している間と, 最後のページでは, しない). */
    private inner class NearEnd : AbsListView.OnScrollListener {
        /**
         * スクロールの状態が変わったとき (使わない).
         *
         * @param view 一覧
         * @param scrollState スクロールの状態
         */
        override fun onScrollStateChanged(
            view: AbsListView,
            scrollState: Int,
        ) = Unit

        /**
         * スクロールしたとき, または, 一覧の中身が変わったとき.
         *
         * @param view 一覧
         * @param firstVisibleItem 見えている, 最初のカード
         * @param visibleItemCount 見えている, カードの数
         * @param totalItemCount カードの数
         */
        override fun onScroll(
            view: AbsListView,
            firstVisibleItem: Int,
            visibleItemCount: Int,
            totalItemCount: Int,
        ) {
            val next = nextUrl
            val nearEnd =
                firstVisibleItem + visibleItemCount >= totalItemCount - COLUMNS * PREFETCH_ROWS
            if (next != null && !loading && nearEnd) {
                fetch(next)
            }
        }
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
