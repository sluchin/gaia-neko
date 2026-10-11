/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.s3.S3Client
import jp.gaianeko.core.CognitoLoginCredentials
import jp.gaianeko.core.DynamoDbSightingSource
import jp.gaianeko.core.DynamoDbSightingStore
import jp.gaianeko.core.S3PhotoSource
import jp.gaianeko.core.S3PhotoStore
import jp.gaianeko.core.SessionManager
import jp.gaianeko.core.SightingUploader
import jp.gaianeko.core.UserPoolAuth
import jp.gaianeko.core.idTokenEmail

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

    private val s3 =
        S3Client {
            region = BuildConfig.AWS_REGION
            credentialsProvider = credentials
        }
    private val dynamoDb =
        DynamoDbClient {
            region = BuildConfig.AWS_REGION
            credentialsProvider = credentials
        }

    /** 撮影した目撃を, S3 と DynamoDB に保存する (ログインした利用者の認証情報で). */
    val uploader =
        SightingUploader(
            S3PhotoStore(s3, BuildConfig.BUCKET_NAME),
            DynamoDbSightingStore(dynamoDb, BuildConfig.TABLE_NAME),
            credentials::identityId,
            { manager.session?.idToken?.let(::idTokenEmail) },
        )

    /** 全員の目撃を読み出す (管理者のロールの認証情報のときだけ, 読める). */
    val sightings = DynamoDbSightingSource(dynamoDb, BuildConfig.TABLE_NAME)

    /** 写真を読み出す (管理者のロールの認証情報のときだけ, 読める). */
    val photos = S3PhotoSource(s3, BuildConfig.BUCKET_NAME)
}
