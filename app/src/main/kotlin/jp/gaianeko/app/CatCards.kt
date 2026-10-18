/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import jp.gaianeko.core.AdoptionCat

/** 一覧の列の数. */
private const val COLUMNS = 2

/** カードとカードの間の余白 (dp). */
private const val CARD_GAP_DP = 8

/** 募集が終わった (譲渡済み) 猫の, 写真と文字の濃さ. */
private const val CLOSED_ALPHA = 0.5f

/** 募集が終わった (譲渡済み) 猫の写真を, 灰色にする. */
private val GRAY = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

/**
 * 猫のカード (写真, 状態, 名前, 年齢と性別, お気に入りのハート) を, 2 列に並べる一覧を作る (里親募集と,
 * お気に入りの画面). カードを押すと, サイトの詳しいページを, ブラウザで開く. 募集が終わった (譲渡済み)
 * お気に入りは, 灰色にして, 「譲渡済み」の印を付ける.
 *
 * @receiver コンテキスト
 * @param cats 猫 (アダプタが, そのまま使う. 変えたら, アダプタに知らせる)
 * @param images 写真の読み込み
 * @param favorites お気に入り
 * @return 一覧と, そのアダプタ
 */
fun Context.catGrid(
    cats: List<AdoptionCat>,
    images: ImageLoader,
    favorites: Favorites,
): Pair<GridView, ArrayAdapter<AdoptionCat>> {
    val adapter = CatAdapter(this, cats, images, favorites)
    val grid =
        GridView(this).apply {
            numColumns = COLUMNS
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(CARD_GAP_DP)
            verticalSpacing = dp(CARD_GAP_DP)
            clipToPadding = false
            this.adapter = adapter
            setOnItemClickListener { _, _, position, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, cats[position].url.toUri()))
            }
        }
    return grid to adapter
}

/**
 * 猫のカードを並べる, 一覧のアダプタ.
 *
 * @param context コンテキスト
 * @property cats 猫
 * @property images 写真の読み込み
 * @property favorites お気に入り (ハートのボタンで, 足し引きする)
 */
private class CatAdapter(
    context: Context,
    private val cats: List<AdoptionCat>,
    private val images: ImageLoader,
    private val favorites: Favorites,
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
        // 募集が終わった (譲渡済み) お気に入りは, 灰色にして, 状態の印を「譲渡済み」にする
        val closed = favorites.isClosed(cat.url)
        card.findViewById<TextView>(R.id.status).showOrHide(
            if (closed) context.getString(R.string.adopted) else cat.status.orEmpty(),
        )
        val photo = card.findViewById<ImageView>(R.id.photo)
        images.load(photo, cat.imageUrl)
        photo.colorFilter = if (closed) GRAY else null
        val alpha = if (closed) CLOSED_ALPHA else 1f
        listOf(R.id.photo, R.id.name, R.id.details).forEach {
            card.findViewById<View>(it).alpha =
                alpha
        }
        val favorite = favorites.contains(cat.url)
        card.findViewById<ImageButton>(R.id.favorite).apply {
            setImageResource(
                if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
            )
            contentDescription =
                context.getString(if (favorite) R.string.favorite_remove else R.string.favorite_add)
            setOnClickListener { favorites.toggle(cat) }
        }
        return card
    }
}
