/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 試験用の ID トークンを作る (本体だけが, 意味を持つ. 署名は, 検証しないため, 適当).
 *
 * @param payload 本体の JSON
 * @return JWT の形の文字列
 */
fun fakeIdToken(payload: String): String {
    val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    return "header.$body.signature"
}

/** [Session] と [idTokenGroups] の単体テスト. */
class SessionTest {
    /** グループが 1 つ以上あれば, 名前を, 順に取り出す. */
    @Test
    fun groupsAreListedInOrder() {
        val token = fakeIdToken("""{"sub":"u","cognito:groups":["admin","tester"]}""")

        assertEquals(listOf("admin", "tester"), idTokenGroups(token))
    }

    /** グループの項目がないトークンは, 空. */
    @Test
    fun noGroupsClaim() {
        assertEquals(emptyList(), idTokenGroups(fakeIdToken("""{"sub":"u"}""")))
    }

    /** 本体がない (ドットがない), 本体が読めない (base64 でない) トークンは, 空. */
    @Test
    fun brokenTokens() {
        assertEquals(emptyList(), idTokenGroups("abc"))
        assertEquals(emptyList(), idTokenGroups("header.!!!.signature"))
    }

    /** グループ admin に入っていれば, 管理者. */
    @Test
    fun adminIsDetected() {
        val admin = Session(fakeIdToken("""{"cognito:groups":["admin"]}"""), "a", "r", 1)
        val user = Session(fakeIdToken("""{"cognito:groups":["other"]}"""), "a", "r", 1)
        val none = Session(fakeIdToken("""{"sub":"u"}"""), "a", "r", 1)

        assertTrue(admin.isAdmin)
        assertFalse(user.isAdmin)
        assertFalse(none.isAdmin)
    }

    /** データクラスの, 比較, 文字列, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val session = Session("id", "access", "refresh", 5)
        assertEquals(session, session.copy())
        assertEquals(session.hashCode(), session.copy().hashCode())
        assertTrue(session.toString().contains("expiresAt=5"))
        assertEquals(
            listOf("id", "access", "refresh"),
            listOf(session.component1(), session.component2(), session.component3()),
        )
        assertEquals(5L, session.component4())
    }

    /** ID トークンから, メールアドレスを取り出す. ないときや, 読めないときは, null. */
    @Test
    fun emailIsReadFromTheIdToken() {
        assertEquals(
            "a@example.com",
            idTokenEmail(fakeIdToken("""{"sub":"u","email":"a@example.com"}""")),
        )
        assertNull(idTokenEmail(fakeIdToken("""{"sub":"u"}""")))
        assertNull(idTokenEmail("abc"))
    }
}
