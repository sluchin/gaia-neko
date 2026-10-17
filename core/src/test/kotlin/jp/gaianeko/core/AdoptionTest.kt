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

/** [parseAdoptionList] の単体テスト (サイトと同じ作りの, ダミーの HTML で確認する). */
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
    private fun page(vararg items: String): String {
        val list = items.joinToString("")
        return """<html><body><div class="jet-listing-grid__items">$list</div></body></html>"""
    }

    /** 名前, 年齢, 性別, 状態, 詳細のページ, 写真を読み出す. */
    @Test
    fun parseAdoptionListReadsAllFields() {
        val cats = parseAdoptionList(page(item()))

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
    fun parseAdoptionListKeepsTheOrder() {
        val cats =
            parseAdoptionList(
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
    fun parseAdoptionListResolvesTheLink() {
        val cats = parseAdoptionList(page(item(href = "/satooya/tama/#respond")))

        assertEquals("https://nyantomo.jp/satooya/tama/", cats.single().url)
    }

    /** 詳細のページへのリンクがない項目は, 飛ばす. */
    @Test
    fun parseAdoptionListSkipsItemWithoutLink() {
        assertEquals(emptyList(), parseAdoptionList(page(item(href = null))))
    }

    /** 名前がない項目は, 飛ばす. */
    @Test
    fun parseAdoptionListSkipsItemWithoutName() {
        assertEquals(emptyList(), parseAdoptionList(page(item(fields = labeled("年齢", "約1歳")))))
    }

    /** 年齢と性別がなければ, null. */
    @Test
    fun parseAdoptionListWithoutAgeAndSex() {
        val cat = parseAdoptionList(page(item(fields = labeled("名前", "タマ")))).single()

        assertNull(cat.age)
        assertNull(cat.sex)
    }

    /** 値が空, 値の部品がない, 見出しが部品に入っていないときは, null. */
    @Test
    fun parseAdoptionListWithMissingValues() {
        val fields =
            labeled("名前", "タマ") + labeled("年齢", " ") +
                """<p class="elementor-heading-title">性別</p>"""
        val cat = parseAdoptionList(page(item(fields = fields))).single()

        assertNull(cat.age)
        assertNull(cat.sex)
    }

    /** 見出しの部品の, 次の部品がなければ, null. */
    @Test
    fun parseAdoptionListWithoutValueElement() {
        val fields =
            labeled("名前", "タマ") +
                """<div><div class="elementor-element"><p class="elementor-heading-title">年齢</p></div></div>"""
        val cat = parseAdoptionList(page(item(fields = fields))).single()

        assertNull(cat.age)
    }

    /** 状態がない, または空なら, null. */
    @Test
    fun parseAdoptionListWithoutStatus() {
        val cats =
            parseAdoptionList(
                page(item(status = ""), item(status = """<div class="centering"> </div>""")),
            )

        assertEquals(listOf(null, null), cats.map { it.status })
    }

    /** `data-src` がなければ, `src` の写真を使う. */
    @Test
    fun parseAdoptionListUsesTheSource() {
        val cat = parseAdoptionList(page(item(image = """<img src="/b.jpeg">"""))).single()

        assertEquals("https://nyantomo.jp/b.jpeg", cat.imageUrl)
    }

    /** 写真がない, または, 埋め込みの絵 (data:) だけなら, null. */
    @Test
    fun parseAdoptionListWithoutImage() {
        val cats =
            parseAdoptionList(
                page(
                    item(image = ""),
                    item(image = """<img src="data:image/png;base64,AAAA">"""),
                ),
            )

        assertEquals(listOf(null, null), cats.map { it.imageUrl })
    }

    /** 一覧はあるが, 猫がいなければ, 空. */
    @Test
    fun parseAdoptionListOfEmptyList() {
        assertEquals(emptyList(), parseAdoptionList(page()))
    }

    /** 一覧が見つからなければ (サイトの作りが変わったら), 失敗する. */
    @Test
    fun parseAdoptionListFailsWithoutList() {
        assertFailsWith<AdoptionParseException> { parseAdoptionList("<html><body></body></html>") }
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
