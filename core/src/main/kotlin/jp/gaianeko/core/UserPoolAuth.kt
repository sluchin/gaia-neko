/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.confirmSignUp
import aws.sdk.kotlin.services.cognitoidentityprovider.initiateAuth
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.signUp

/** 認証のパラメータ名: メールアドレス. */
private const val PARAM_USERNAME = "USERNAME"

/** 認証のパラメータ名: パスワード. */
private const val PARAM_PASSWORD = "PASSWORD"

/** 認証のパラメータ名: 更新トークン. */
private const val PARAM_REFRESH_TOKEN = "REFRESH_TOKEN"

/**
 * Cognito のユーザープールで, 利用者の登録とログインをする.
 *
 * ユーザー名は, メールアドレス (ユーザープールの username-attributes が email). 認証の流れは,
 * USER_PASSWORD_AUTH (パスワードを, HTTPS の上で送る).
 *
 * @property client Cognito Identity Provider のクライアント
 * @property clientId アプリクライアントの ID
 * @property now 今の時刻 (UNIX 時刻 (s)) を返す関数 (テストで, 差し替える)
 */
class UserPoolAuth(
    private val client: CognitoIdentityProviderClient,
    private val clientId: String,
    private val now: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    /**
     * 利用者を登録する (メールに, 確認コードが届く).
     *
     * @param email メールアドレス
     * @param password パスワード
     */
    suspend fun signUp(
        email: String,
        password: String,
    ) {
        client.signUp {
            clientId = this@UserPoolAuth.clientId
            username = email
            this.password = password
        }
    }

    /**
     * メールに届いた確認コードで, 登録を完了する.
     *
     * @param email メールアドレス
     * @param code 確認コード
     */
    suspend fun confirmSignUp(
        email: String,
        code: String,
    ) {
        client.confirmSignUp {
            clientId = this@UserPoolAuth.clientId
            username = email
            confirmationCode = code
        }
    }

    /**
     * ログインする.
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return トークン
     * @throws IllegalStateException Cognito が, 追加の確認 (チャレンジ) を求めたとき, または, 応答に必要な項目がないとき
     */
    suspend fun signIn(
        email: String,
        password: String,
    ): Session =
        authenticate(
            AuthFlowType.UserPasswordAuth,
            mapOf(PARAM_USERNAME to email, PARAM_PASSWORD to password),
            null,
        )

    /**
     * 更新トークンで, ID トークンとアクセストークンを取り直す.
     *
     * @param session 今のトークン
     * @return 新しいトークン (更新トークンは, 今のものを引き継ぐ)
     * @throws IllegalStateException 応答に, 必要な項目がないとき
     */
    suspend fun refresh(session: Session): Session =
        authenticate(
            AuthFlowType.RefreshTokenAuth,
            mapOf(PARAM_REFRESH_TOKEN to session.refreshToken),
            session.refreshToken,
        )

    /**
     * 認証して, トークンを受け取る.
     *
     * @param flow 認証の流れ
     * @param parameters 認証のパラメータ
     * @param inheritedRefreshToken 応答に更新トークンがないときに使う, 今の更新トークン (ログインでは, null)
     * @return トークン
     * @throws IllegalStateException 結果がない (チャレンジを求められた), または, 項目がないとき
     */
    private suspend fun authenticate(
        flow: AuthFlowType,
        parameters: Map<String, String>,
        inheritedRefreshToken: String?,
    ): Session {
        val response =
            client.initiateAuth {
                clientId = this@UserPoolAuth.clientId
                authFlow = flow
                authParameters = parameters
            }
        val result = checkNotNull(response.authenticationResult) { "Cognito requires a challenge" }
        return Session(
            idToken = required(result.idToken, "ID token"),
            accessToken = required(result.accessToken, "access token"),
            refreshToken = required(result.refreshToken ?: inheritedRefreshToken, "refresh token"),
            expiresAt = now() + result.expiresIn,
        )
    }
}
