/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 試験の, 今の時刻 (UNIX 時刻 (s)). */
private const val NOW = 1_000_000L

/** 試験の, ID トークンの有効な長さ (1 時間. 秒). */
private const val VALID_SEC = 3600L

/** 試験用の更新トークンの保存先 (メモリ). */
private class MemoryStore(
    var token: String? = null,
) : RefreshTokenStore {
    override fun load(): String? = token

    override fun save(token: String?) {
        this.token = token
    }
}

/** [SessionManager] の単体テスト (ユーザープールの操作は, 偽物). */
class SessionManagerTest {
    private val auth = mockk<UserPoolAuth>()
    private val store = MemoryStore()
    private var nowSec = NOW
    private val manager = SessionManager(auth, store) { nowSec }

    /** 期限が, 今から VALID_SEC 秒後のトークンを作る. */
    private fun session(
        id: String = "id",
        refresh: String = "refresh",
    ) = Session(id, "access", refresh, nowSec + VALID_SEC)

    /** ログインすると, 今のトークンになり, 更新トークンを保存する. */
    @Test
    fun signInKeepsTheSessionAndSavesTheRefreshToken() =
        runTest {
            coEvery { auth.signIn("a@example.com", "pass") } returns session()

            val signedIn = manager.signIn("a@example.com", "pass")

            assertEquals(signedIn, manager.session)
            assertEquals("refresh", store.token)
        }

    /** 保存した更新トークンがあれば, 取り直して, ログインの状態に戻す. */
    @Test
    fun restoreRefreshesWithTheSavedToken() =
        runTest {
            store.token = "saved"
            coEvery { auth.refresh("saved") } returns session(refresh = "saved")

            val restored = manager.restore()

            assertEquals(restored, manager.session)
            assertEquals("saved", store.token)
        }

    /** 更新トークンを保存していなければ, 何もせずに null. */
    @Test
    fun restoreWithoutTheSavedToken() =
        runTest {
            assertNull(manager.restore())
            assertNull(manager.session)
        }

    /** 更新トークンが無効 (期限切れ) なら, 保存を消して, null. */
    @Test
    fun restoreWithAnInvalidToken() =
        runTest {
            store.token = "old"
            coEvery { auth.refresh("old") } throws NotAuthorizedException { message = "expired" }

            assertNull(manager.restore())
            assertNull(store.token)
        }

    /** 通信の失敗は, 呼び出し元に伝える (保存した更新トークンは, 消さない). */
    @Test
    fun restoreKeepsTheTokenOnNetworkFailure() =
        runTest {
            store.token = "saved"
            coEvery { auth.refresh("saved") } throws IOException("offline")

            assertFailsWith<IOException> { manager.restore() }
            assertEquals("saved", store.token)
        }

    /** 期限まで余裕があれば, 取り直さずに, 今の ID トークンを返す. */
    @Test
    fun idTokenIsReusedBeforeTheMargin() =
        runTest {
            coEvery { auth.signIn(any(), any()) } returns session()
            manager.signIn("a@example.com", "pass")
            nowSec = NOW + VALID_SEC - 301

            assertEquals("id", manager.idToken())
            coVerify(exactly = 0) { auth.refresh(any()) }
        }

    /** 期限が 5 分前に近づいたら, 取り直した ID トークンを返す. */
    @Test
    fun idTokenIsRefreshedNearTheExpiry() =
        runTest {
            coEvery { auth.signIn(any(), any()) } returns session()
            manager.signIn("a@example.com", "pass")
            nowSec = NOW + VALID_SEC - 300
            coEvery { auth.refresh("refresh") } returns session(id = "id2")

            assertEquals("id2", manager.idToken())
        }

    /** ログインしていなければ, 例外. */
    @Test
    fun idTokenWithoutSignIn() =
        runTest {
            val error = assertFailsWith<IllegalStateException> { manager.idToken() }

            assertEquals("Not signed in", error.message)
        }

    /** 更新トークンの期限が切れていれば, ログアウトして, 例外. */
    @Test
    fun idTokenWithAnExpiredRefreshToken() =
        runTest {
            coEvery { auth.signIn(any(), any()) } returns session()
            manager.signIn("a@example.com", "pass")
            nowSec = NOW + VALID_SEC
            coEvery { auth.refresh("refresh") } throws
                NotAuthorizedException { message = "expired" }

            val error = assertFailsWith<IllegalStateException> { manager.idToken() }

            assertEquals("The session has expired", error.message)
            assertNull(manager.session)
        }

    /** ログアウトすると, トークンと, 保存した更新トークンが消える. */
    @Test
    fun signOutClearsEverything() =
        runTest {
            coEvery { auth.signIn(any(), any()) } returns session()
            manager.signIn("a@example.com", "pass")

            manager.signOut()

            assertNull(manager.session)
            assertNull(store.token)
        }

    /** 時計を渡さないときは, 実際の時計を使う. */
    @Test
    fun idTokenUsesTheSystemClockByDefault() =
        runTest {
            val real = SessionManager(auth, store)
            coEvery { auth.signIn(any(), any()) } returns
                Session("id", "access", "refresh", System.currentTimeMillis() / 1000 + VALID_SEC)
            real.signIn("a@example.com", "pass")

            assertEquals("id", real.idToken())
        }
}
