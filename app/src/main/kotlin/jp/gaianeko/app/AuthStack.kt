/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import jp.gaianeko.core.CognitoLoginCredentials
import jp.gaianeko.core.SessionManager
import jp.gaianeko.core.UserPoolAuth

/**
 * ログインに使うものを, まとめて作る (設定は, BuildConfig. local.properties から).
 *
 * @param context コンテキスト
 */
class AuthStack(
    context: Context,
) {
    /** ユーザープールの操作 (登録, 確認). */
    val auth =
        UserPoolAuth(
            CognitoIdentityProviderClient { region = BuildConfig.AWS_REGION },
            BuildConfig.CLIENT_ID,
        )

    /** ログインの状態. */
    val manager = SessionManager(auth, SecureRefreshTokenStore(context))

    /** ログインした利用者の, AWS の一時的な認証情報 (S3 と DynamoDB のクライアントに渡す). */
    val credentials =
        CognitoLoginCredentials(
            CognitoIdentityClient { region = BuildConfig.AWS_REGION },
            BuildConfig.IDENTITY_POOL_ID,
            "cognito-idp.${BuildConfig.AWS_REGION}.amazonaws.com/${BuildConfig.USER_POOL_ID}",
            manager::idToken,
        )
}
