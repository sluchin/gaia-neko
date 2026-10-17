/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** [parseAdoptionPage] の単体テスト (サイトと同じ作りの, ダミーの HTML で確認する). */
class AdoptionTest {
    /** 見出しと値の組 (サイトの Elementor の部品と, 同じ入れ子). */
    private fun labeled(
        label: String,
        value: String,
    ): String =
        """
        <div class="elementor-element"><p class="elementor-heading-title">$label</p></div>
        <div class="elementor-element"><div class="jet-listing-dynamic-field__content">$value</div></div>
        """

    /** 1 匹の項目. */
    private fun item(
        href: String? = "https://example.com/satooya/tama/",
        image: String = LAZY_IMAGE,
        status: String = """<div class="centering">里親募集中</div>""",
        fields: String = ALL_FIELDS,
    ): String {
        val link =
            href?.let { """<a class="jet-listing-dynamic-image__link" href="$it">$image</a>""" }
                ?: image
        val body = "$status$link<section>$fields</section>"
        return """<div class="jet-listing-grid__item">$body</div>"""
    }

    /** 一覧のページ. */
    private fun page(vararg items: String): String = pageOf("", "", *items)

    /** ページの番号と, 頭の部分を付けた, 一覧のページ. */
    private fun pageOf(
        head: String,
        pages: String,
        vararg items: String,
    ): String {
        val list = items.joinToString("")
        val body = """<div class="jet-listing-grid__items"$pages>$list</div>"""
        return "<html><head>$head</head><body>$body</body></html>"
    }

    /** 一覧のページの, 猫だけを読み出す. */
    private fun catsOf(html: String): List<AdoptionCat> = parseAdoptionPage(html).cats

    /** 名前, 年齢, 性別, 状態, 詳細のページ, 写真を読み出す. */
    @Test
    fun parseAdoptionPageReadsAllFields() {
        val cats = catsOf(page(item()))

        assertEquals(
            listOf(
                AdoptionCat(
                    name = "テスト タマ",
                    age = "約1歳",
                    sex = "メス",
                    status = "里親募集中",
                    url = "https://example.com/satooya/tama/",
                    imageUrl = "https://example.com/a.jpeg",
                ),
            ),
            cats,
        )
    }

    /** 項目は, ページの順に並べる. */
    @Test
    fun parseAdoptionPageKeepsTheOrder() {
        val cats =
            catsOf(
                page(
                    item(href = "https://example.com/satooya/a/"),
                    item(href = "https://example.com/satooya/b/"),
                ),
            )

        assertEquals(
            listOf("https://example.com/satooya/a/", "https://example.com/satooya/b/"),
            cats.map { it.url },
        )
    }

    /** 相対のリンクは, ページの URL で解決して, `#` から後ろを除く. */
    @Test
    fun parseAdoptionPageResolvesTheLink() {
        val cats = catsOf(page(item(href = "/satooya/tama/#respond")))

        assertEquals("https://nyantomo.jp/satooya/tama/", cats.single().url)
    }

    /** 詳細のページへのリンクがない項目は, 飛ばす. */
    @Test
    fun parseAdoptionPageSkipsItemWithoutLink() {
        assertEquals(emptyList(), catsOf(page(item(href = null))))
    }

    /** 名前がない項目は, 飛ばす. */
    @Test
    fun parseAdoptionPageSkipsItemWithoutName() {
        assertEquals(emptyList(), catsOf(page(item(fields = labeled("年齢", "約1歳")))))
    }

    /** 年齢と性別がなければ, null. */
    @Test
    fun parseAdoptionPageWithoutAgeAndSex() {
        val cat = catsOf(page(item(fields = labeled("名前", "タマ")))).single()

        assertNull(cat.age)
        assertNull(cat.sex)
    }

    /** 値が空, 値の部品がない, 見出しが部品に入っていないときは, null. */
    @Test
    fun parseAdoptionPageWithMissingValues() {
        val fields =
            labeled("名前", "タマ") + labeled("年齢", " ") +
                """<p class="elementor-heading-title">性別</p>"""
        val cat = catsOf(page(item(fields = fields))).single()

        assertNull(cat.age)
        assertNull(cat.sex)
    }

    /** 見出しの部品の, 次の部品がなければ, null. */
    @Test
    fun parseAdoptionPageWithoutValueElement() {
        val fields =
            labeled("名前", "タマ") +
                """<div><div class="elementor-element"><p class="elementor-heading-title">年齢</p></div></div>"""
        val cat = catsOf(page(item(fields = fields))).single()

        assertNull(cat.age)
    }

    /** 状態がない, または空なら, null. */
    @Test
    fun parseAdoptionPageWithoutStatus() {
        val cats =
            catsOf(
                page(item(status = ""), item(status = """<div class="centering"> </div>""")),
            )

        assertEquals(listOf(null, null), cats.map { it.status })
    }

    /** `data-src` がなければ, `src` の写真を使う. */
    @Test
    fun parseAdoptionPageUsesTheSource() {
        val cat = catsOf(page(item(image = """<img src="/b.jpeg">"""))).single()

        assertEquals("https://nyantomo.jp/b.jpeg", cat.imageUrl)
    }

    /** 写真がない, または, 埋め込みの絵 (data:) だけなら, null. */
    @Test
    fun parseAdoptionPageWithoutImage() {
        val cats =
            catsOf(
                page(
                    item(image = ""),
                    item(image = """<img src="data:image/png;base64,AAAA">"""),
                ),
            )

        assertEquals(listOf(null, null), cats.map { it.imageUrl })
    }

    /** 一覧はあるが, 猫がいなければ, 空. */
    @Test
    fun parseAdoptionPageOfEmptyList() {
        assertEquals(emptyList(), catsOf(page()))
    }

    /** 次のページは, canonical の固定ページに, ページの番号を付ける (問い合わせは, 除く). */
    @Test
    fun parseAdoptionPageReadsTheNextPage() {
        val head = """<link rel="canonical" href="https://example.com/list/?x=1">"""
        val html = pageOf(head, """ data-page="2" data-pages="18"""", item())

        assertEquals(
            "https://example.com/list/?jsf=jet-engine&pagenum=3",
            parseAdoptionPage(html).nextUrl,
        )
    }

    /** canonical がない, または空なら, ページの URL に, ページの番号を付ける. */
    @Test
    fun parseAdoptionPageWithoutCanonical() {
        val pages = """ data-page="1" data-pages="2""""
        val empty = """<link rel="canonical" href="">"""

        assertEquals(
            listOf(
                "https://example.com/a/?jsf=jet-engine&pagenum=2",
                "https://example.com/a/?jsf=jet-engine&pagenum=2",
            ),
            listOf(pageOf("", pages), pageOf(empty, pages)).map {
                parseAdoptionPage(it, "https://example.com/a/").nextUrl
            },
        )
    }

    /** 最後のページ, ページの番号がない, 番号が数でないときは, 次のページはない. */
    @Test
    fun parseAdoptionPageOfLastPage() {
        val pages =
            listOf(
                """ data-page="18" data-pages="18"""",
                "",
                """ data-page="x" data-pages="y"""",
            )

        assertEquals(
            listOf(null, null, null),
            pages.map { parseAdoptionPage(pageOf("", it)).nextUrl },
        )
    }

    /** 一覧が見つからなければ (サイトの作りが変わったら), 失敗する. */
    @Test
    fun parseAdoptionPageFailsWithoutList() {
        assertFailsWith<AdoptionParseException> { parseAdoptionPage("<html><body></body></html>") }
    }

    /** ページのデータクラスの, 文字列, 複製, 分解が, 使える. */
    @Test
    fun pageDataClassFeatures() {
        val page = AdoptionPage(emptyList(), "https://example.com/")

        assertEquals(page, page.copy())
        assertEquals(page.hashCode(), page.copy().hashCode())
        assertEquals("AdoptionPage(cats=[], nextUrl=https://example.com/)", page.toString())
        assertEquals(
            listOf(emptyList<AdoptionCat>(), "https://example.com/"),
            listOf(page.component1(), page.component2()),
        )
    }

    /** データクラスの, 文字列, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val cat = AdoptionCat("タマ", "約1歳", "メス", "里親募集中", "https://example.com/", null)

        assertEquals(cat, cat.copy())
        assertEquals(cat.hashCode(), cat.copy().hashCode())
        assertEquals(
            "AdoptionCat(name=タマ, age=約1歳, sex=メス, status=里親募集中, url=https://example.com/, " +
                "imageUrl=null)",
            cat.toString(),
        )
        assertEquals(
            listOf("タマ", "約1歳", "メス", "里親募集中", "https://example.com/", null),
            listOf(
                cat.component1(),
                cat.component2(),
                cat.component3(),
                cat.component4(),
                cat.component5(),
                cat.component6(),
            ),
        )
    }

    private companion object {
        /** 遅れて読み込む写真 (src は埋め込みの絵で, data-src に本当の URL がある). */
        const val LAZY_IMAGE =
            """<img src="data:image/png;base64,AAAA" data-src="https://example.com/a.jpeg">"""

        /** 名前, 年齢, 性別の, 全ての見出しと値. */
        const val ALL_FIELDS =
            """
            <div class="elementor-element"><p class="elementor-heading-title">名前</p></div>
            <div class="elementor-element">テスト タマ</div>
            <div class="elementor-element"><p class="elementor-heading-title">年齢</p></div>
            <div class="elementor-element">約1歳</div>
            <div class="elementor-element"><p class="elementor-heading-title">性別</p></div>
            <div class="elementor-element">メス</div>
            """
    }
}
