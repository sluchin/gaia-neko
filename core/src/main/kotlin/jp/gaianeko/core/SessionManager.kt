/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.cognitoidentityprovider.model.NotAuthorizedException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** ID トークンの期限の何秒前から, 取り直すか (5 分). */
private const val REFRESH_MARGIN_SEC = 300L

/** 更新トークンを, 端末に保存する入り口 (Android では, 暗号化して保存する. テストでは, 偽物). */
interface RefreshTokenStore {
    /**
     * 保存した更新トークンを読む.
     *
     * @return 更新トークン. 保存していない, または, 読めないときは, null
     */
    fun load(): String?

    /**
     * 更新トークンを保存する.
     *
     * @param token 更新トークン. null なら, 消す
     */
    fun save(token: String?)
}

/**
 * ログインの状態を持つ (トークンと, 更新トークンの保存).
 *
 * ログインすると, 更新トークンを保存する. アプリを開き直したときは, 保存した更新トークンで, ログインし直さずに,
 * トークンを取り直す ([restore]). ID トークンの期限が近いときは, [idToken] が, 取り直す.
 *
 * @property auth ユーザープールの操作
 * @property store 更新トークンの保存先
 * @property now 今の時刻 (UNIX 時刻 (s)) を返す関数 (テストで, 差し替える)
 */
class SessionManager(
    private val auth: UserPoolAuth,
    private val store: RefreshTokenStore,
    private val now: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    private val mutex = Mutex()
    private var current: Session? = null

    /** 今のトークン (ログインしていなければ, null). */
    val session: Session? get() = current

    /**
     * ログインする (更新トークンを, 保存する).
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return トークン
     */
    suspend fun signIn(
        email: String,
        password: String,
    ): Session = mutex.withLock { adopt(auth.signIn(email, password)) }

    /**
     * 保存した更新トークンで, ログインの状態に戻す.
     *
     * @return トークン. 保存した更新トークンがない, または, 期限が切れている (無効な) ときは, null
     */
    suspend fun restore(): Session? = mutex.withLock { store.load()?.let { refreshLocked(it) } }

    /**
     * 有効な ID トークンを返す (期限が近ければ, 取り直す).
     *
     * @return ID トークン
     * @throws IllegalStateException ログインしていない, または, 更新トークンの期限が切れているとき
     */
    suspend fun idToken(): String =
        mutex.withLock {
            val now = checkNotNull(current) { "Not signed in" }
            if (this.now() + REFRESH_MARGIN_SEC < now.expiresAt) {
                now.idToken
            } else {
                checkNotNull(refreshLocked(now.refreshToken)) { "The session has expired" }.idToken
            }
        }

    /** ログアウトする (保存した更新トークンを, 消す). */
    fun signOut() {
        current = null
        store.save(null)
    }

    /**
     * 更新トークンで, トークンを取り直す (ロックを, 取った状態で呼ぶ).
     *
     * @param refreshToken 更新トークン
     * @return トークン. 更新トークンが無効なとき (期限切れ, 取り消し) は, ログアウトして, null
     */
    private suspend fun refreshLocked(refreshToken: String): Session? =
        try {
            adopt(auth.refresh(refreshToken))
        } catch (
            @Suppress("SwallowedException") e: NotAuthorizedException,
        ) {
            // 更新トークンが無効なときだけ, ログアウトする (通信の失敗は, 呼び出し元に伝える)
            signOut()
            null
        }

    /**
     * 受け取ったトークンを, 今のトークンにして, 更新トークンを保存する.
     *
     * @param session 受け取ったトークン
     * @return 同じトークン
     */
    private fun adopt(session: Session): Session {
        current = session
        store.save(session.refreshToken)
        return session
    }
}
