/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** 里親募集の一覧の, 最初のページ (ニャン友ねっとわーく北海道. 一覧の固定ページに転送される). */
const val ADOPTION_URL = "https://nyantomo.jp/satooya/"

/** 2 ページ目からの, ページの番号を付ける問い合わせ (JetSmartFilters のページ送り. 後ろに番号を付ける). */
private const val PAGE_QUERY = "?jsf=jet-engine&pagenum="

/** 一覧の, 全ての猫の数 (JetSmartFilters が, ページの中のスクリプトに書く `found_posts`). */
private val FOUND_POSTS = Regex(""""found_posts":(\d+)""")

/** 里親を募集している猫の, 状態の文字 (この文字で始まれば, 募集している). */
private const val STATUS_OPEN = "里親募集中"

/** 一覧の項目の, 名前の見出し. */
private const val LABEL_NAME = "名前"

/** 一覧の項目の, 年齢の見出し. */
private const val LABEL_AGE = "年齢"

/** 一覧の項目の, 性別の見出し. */
private const val LABEL_SEX = "性別"

/**
 * 里親を募集している猫 (一覧のページの, 1 匹).
 *
 * 写真は, URL だけを持つ (写真そのものは, 表示するときに, サイトから取得する).
 *
 * @property name 名前
 * @property age 年齢 (例: 約4ヵ月. なければ, null)
 * @property sex 性別 (なければ, null)
 * @property status 状態 (例: 里親募集中. なければ, null)
 * @property url 詳細のページの URL
 * @property imageUrl 写真の URL (なければ, null)
 */
data class AdoptionCat(
    val name: String,
    val age: String?,
    val sex: String?,
    val status: String?,
    val url: String,
    val imageUrl: String?,
)

/**
 * 里親募集の一覧の, 1 ページ (サイトは, 1 ページに 15 匹ずつ並べる).
 *
 * @property cats 猫 (ページの順)
 * @property nextUrl 次のページの URL (最後のページなら, null)
 * @property total 一覧の, 全てのページの猫の数 (分からなければ, null)
 */
data class AdoptionPage(
    val cats: List<AdoptionCat>,
    val nextUrl: String?,
    val total: Int?,
)

/**
 * 一覧のページを, 読めなかったとき (サイトの作りが, 変わったとき).
 *
 * @param message 理由
 */
class AdoptionParseException(
    message: String,
) : IllegalStateException(message)

/**
 * 里親募集の一覧のページ (HTML) から, 猫と, 次のページの URL を読み出す.
 *
 * ページは, WordPress (Elementor と JetEngine) の一覧. 1 匹ごとの項目に, 写真, 詳細のページへのリンク,
 * 「名前」「年齢」「性別」の見出しと, その次の値, 状態がある. 名前か, リンクのない項目は, 飛ばす.
 * 一覧の部品の `data-page` と `data-pages` が, 今のページと, ページの数. 次のページは, 一覧の固定ページ
 * (`canonical` のリンク. なければ, [baseUrl]) に, ページの番号の問い合わせを付けた URL. 全ての猫の数は,
 * ページの中のスクリプトの `"found_posts":263` のような値.
 *
 * @param html 一覧のページの HTML
 * @param baseUrl 相対の URL を解決する, ページの URL
 * @return 猫, 次のページの URL, 全ての猫の数
 * @throws AdoptionParseException 一覧が見つからないとき (サイトの作りが, 変わったとき)
 */
fun parseAdoptionPage(
    html: String,
    baseUrl: String = ADOPTION_URL,
): AdoptionPage {
    val document = Jsoup.parse(html, baseUrl)
    val list =
        document.selectFirst(".jet-listing-grid__items")
            ?: throw AdoptionParseException("The list of the cats is not found")
    val page = list.attr("data-page").toIntOrNull() ?: 1
    val pages = list.attr("data-pages").toIntOrNull() ?: 1
    val listUrl =
        document
            .selectFirst("link[rel=canonical]")
            ?.absUrl("href")
            .orEmpty()
            .ifEmpty { baseUrl }
    val nextUrl = "${listUrl.substringBefore('?')}$PAGE_QUERY${page + 1}"
    return AdoptionPage(
        cats = document.select(".jet-listing-grid__item").mapNotNull { parseItem(it) },
        nextUrl = nextUrl.takeIf { page < pages },
        total = FOUND_POSTS.find(html)?.let { it.groupValues[1].toIntOrNull() },
    )
}

/**
 * 1 ページの猫のうち, 知っている猫 (保存した一覧) にない, 新しい猫を返す (詳しいページの URL で比べる).
 *
 * @param known 知っている猫
 * @param page 1 ページの猫
 * @return 新しい猫 (ページの順)
 */
fun newAdoptionCats(
    known: List<AdoptionCat>,
    page: List<AdoptionCat>,
): List<AdoptionCat> {
    val urls = known.mapTo(HashSet()) { it.url }
    return page.filter { it.url !in urls }
}

/**
 * 猫の詳しいページ (HTML) から, まだ里親を募集しているかを返す.
 *
 * 詳しいページは, 状態を, JetEngine の項目 (`.jet-listing-dynamic-field__content`) に書く. その中に,
 * 「里親募集中」で始まる項目があれば, 募集している (「里親募集中 函館市愛護センター」のように, 後ろに,
 * 場所が付くことがある).
 *
 * @param html 詳しいページの HTML
 * @return 募集していれば, true
 */
fun isAdoptionOpen(html: String): Boolean =
    Jsoup
        .parse(html)
        .select(".jet-listing-dynamic-field__content")
        .any { it.text().startsWith(STATUS_OPEN) }

/**
 * 一覧の 1 つの項目を, 読み出す.
 *
 * @param item 項目
 * @return 猫. 名前か, 詳細のページへのリンクがなければ, null
 */
private fun parseItem(item: Element): AdoptionCat? {
    val url = item.selectFirst("a[href*=/satooya/]")?.absUrl("href")?.substringBefore('#')
    val name = field(item, LABEL_NAME)
    if (url == null || name == null) {
        return null
    }
    return AdoptionCat(
        name = name,
        age = field(item, LABEL_AGE),
        sex = field(item, LABEL_SEX),
        status = item.selectFirst(".centering")?.text()?.ifBlank { null },
        url = url,
        imageUrl = imageUrl(item),
    )
}

/**
 * 見出しの, 次の部品の文字を返す (「名前」の次の「伊達 メロン」のように).
 *
 * @param item 項目
 * @param label 見出し
 * @return 値. 見出しか, 値がなければ, null
 */
private fun field(
    item: Element,
    label: String,
): String? =
    item
        .select(".elementor-heading-title")
        .firstOrNull { it.text() == label }
        ?.closest(".elementor-element")
        ?.nextElementSibling()
        ?.text()
        ?.ifBlank { null }

/**
 * 写真の URL を返す (遅れて読み込む写真は, `data-src` に, 本当の URL がある).
 *
 * @param item 項目
 * @return 写真の URL. なければ, null
 */
private fun imageUrl(item: Element): String? {
    val image = item.selectFirst("img") ?: return null
    return listOf(image.absUrl("data-src"), image.absUrl("src")).firstOrNull {
        it.startsWith("http")
    }
}
