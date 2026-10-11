/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.getCredentialsForIdentity
import aws.sdk.kotlin.services.cognitoidentity.getId
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** 期限の何分前から, 認証情報を取り直すか. */
private val REFRESH_BEFORE_EXPIRY: Duration = 5.minutes

/**
 * ログインした利用者の ID トークンから, AWS の一時的な認証情報を受け取る ([CredentialsProvider]).
 *
 * Cognito のユーザープールの ID トークンを, ID プールに渡して, ID (GetId) と, 認証情報
 * (GetCredentialsForIdentity) を受け取る. 管理者のグループに入っていれば, 管理者のロールの認証情報になる.
 * ID は, 最初の 1 回だけ取り, 認証情報は, 期限の 5 分前まで, 使い回す. 同時に呼ばれても, 取得は 1 回にする.
 *
 * @property client Cognito Identity のクライアント
 * @property identityPoolId ID プールの ID
 * @property loginKey ID プールに渡す, ユーザープールの名前 (cognito-idp.リージョン.amazonaws.com/ユーザープール ID)
 * @property idToken 有効な ID トークンを返す関数 (期限が切れていれば, 取り直して返す)
 * @property now 今の時刻を返す関数 (テストで, 差し替える)
 */
class CognitoLoginCredentials(
    private val client: CognitoIdentityClient,
    private val identityPoolId: String,
    private val loginKey: String,
    private val idToken: suspend () -> String,
    private val now: () -> Instant = { Instant.now() },
) : CredentialsProvider {
    private val mutex = Mutex()
    private var cachedIdentityId: String? = null
    private var cached: Cached? = null

    /** 受け取った認証情報と, その期限. */
    private class Cached(
        val credentials: Credentials,
        val expiration: Instant,
    )

    /**
     * ID プールの ID (撮影した利用者の識別子 userId に使う. IAM の権限の絞り込みに使われる ID).
     *
     * @return ID (ap-northeast-1: から始まる文字列)
     * @throws IllegalStateException Cognito の応答に, ID がないとき
     */
    suspend fun identityId(): String = mutex.withLock { identityIdLocked() }

    /**
     * 認証情報を返す (期限の 5 分前までは, 受け取った認証情報を使い回す).
     *
     * @param attributes SDK が渡す属性 (使わない)
     * @return 認証情報
     * @throws IllegalStateException Cognito の応答に, 必要な項目がないとき
     */
    override suspend fun resolve(attributes: Attributes): Credentials =
        mutex.withLock {
            cached?.takeIf { now() + REFRESH_BEFORE_EXPIRY < it.expiration }?.credentials
                ?: fetch().also { cached = it }.credentials
        }

    /**
     * ID を返す (取っていなければ, 取る). ロックを, 取った状態で呼ぶ.
     *
     * @return ID
     * @throws IllegalStateException Cognito の応答に, ID がないとき
     */
    private suspend fun identityIdLocked(): String =
        cachedIdentityId ?: fetchIdentityId().also { cachedIdentityId = it }

    /**
     * Cognito から, 認証情報を受け取る.
     *
     * @return 認証情報と, その期限
     * @throws IllegalStateException Cognito の応答に, 必要な項目がないとき
     */
    private suspend fun fetch(): Cached {
        val id = identityIdLocked()
        val token = idToken()
        val response =
            client.getCredentialsForIdentity {
                identityId = id
                logins = mapOf(loginKey to token)
            }
        val c = required(response.credentials, "credentials")
        val expiration = required(c.expiration, "expiration")
        val credentials =
            Credentials(
                accessKeyId = required(c.accessKeyId, "access key ID"),
                secretAccessKey = required(c.secretKey, "secret key"),
                sessionToken = c.sessionToken,
                expiration = expiration,
            )
        return Cached(credentials, expiration)
    }

    /**
     * ID プールの ID を取る (ID トークンで, ログインした利用者の ID).
     *
     * @return ID
     * @throws IllegalStateException Cognito の応答に, ID がないとき
     */
    private suspend fun fetchIdentityId(): String {
        val token = idToken()
        val response =
            client.getId {
                identityPoolId = this@CognitoLoginCredentials.identityPoolId
                logins = mapOf(loginKey to token)
            }
        return required(response.identityId, "identity ID")
    }
}
