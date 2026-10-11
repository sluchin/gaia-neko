/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthFlowType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.AuthenticationResultType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ChallengeNameType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ConfirmSignUpRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ConfirmSignUpResponse
import aws.sdk.kotlin.services.cognitoidentityprovider.model.InitiateAuthRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.InitiateAuthResponse
import aws.sdk.kotlin.services.cognitoidentityprovider.model.SignUpRequest
import aws.sdk.kotlin.services.cognitoidentityprovider.model.SignUpResponse
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 試験のアプリクライアントの ID. */
private const val CLIENT_ID = "client-1"

/** 試験のメールアドレス. */
private const val EMAIL = "user@example.com"

/** 試験の, 今の時刻 (UNIX 時刻 (s)). */
private const val NOW = 1_000_000L

/** 試験の, ID トークンの有効な長さ (秒). */
private const val EXPIRES_IN = 3600

/** [UserPoolAuth] の単体テスト (Cognito のクライアントは, 偽物). */
class UserPoolAuthTest {
    private val client = mockk<CognitoIdentityProviderClient>()
    private val auth = UserPoolAuth(client, CLIENT_ID) { NOW }

    /** 認証の応答を設定する. */
    private fun givenAuth(result: AuthenticationResultType?) {
        coEvery { client.initiateAuth(any<InitiateAuthRequest>()) } returns
            InitiateAuthResponse {
                authenticationResult = result
                if (result == null) challengeName = ChallengeNameType.NewPasswordRequired
            }
    }

    /** トークンの応答を作る (null の項目は, 入れない). */
    private fun tokens(
        id: String? = "id",
        access: String? = "access",
        refresh: String? = "refresh",
    ) = AuthenticationResultType {
        idToken = id
        accessToken = access
        refreshToken = refresh
        expiresIn = EXPIRES_IN
    }

    /** 登録は, クライアント ID, メールアドレス, パスワードを送る. */
    @Test
    fun signUpSendsTheCredentials() =
        runTest {
            val request = slot<SignUpRequest>()
            coEvery { client.signUp(capture(request)) } returns
                SignUpResponse {
                    userConfirmed = false
                    userSub = "sub"
                }

            auth.signUp(EMAIL, "pass")

            assertEquals(CLIENT_ID, request.captured.clientId)
            assertEquals(EMAIL, request.captured.username)
            assertEquals("pass", request.captured.password)
        }

    /** 確認は, クライアント ID, メールアドレス, 確認コードを送る. */
    @Test
    fun confirmSignUpSendsTheCode() =
        runTest {
            val request = slot<ConfirmSignUpRequest>()
            coEvery { client.confirmSignUp(capture(request)) } returns ConfirmSignUpResponse {}

            auth.confirmSignUp(EMAIL, "123456")

            assertEquals(CLIENT_ID, request.captured.clientId)
            assertEquals(EMAIL, request.captured.username)
            assertEquals("123456", request.captured.confirmationCode)
        }

    /** ログインは, パスワードで認証して, トークンと期限を返す. */
    @Test
    fun signInReturnsTheSession() =
        runTest {
            val request = slot<InitiateAuthRequest>()
            coEvery { client.initiateAuth(capture(request)) } returns
                InitiateAuthResponse { authenticationResult = tokens() }

            val session = auth.signIn(EMAIL, "pass")

            assertEquals(Session("id", "access", "refresh", NOW + EXPIRES_IN), session)
            assertEquals(AuthFlowType.UserPasswordAuth, request.captured.authFlow)
            assertEquals(CLIENT_ID, request.captured.clientId)
            assertEquals(
                mapOf("USERNAME" to EMAIL, "PASSWORD" to "pass"),
                request.captured.authParameters,
            )
        }

    /** 更新は, 更新トークンで認証して, 更新トークンを引き継ぐ (応答に, 更新トークンがないため). */
    @Test
    fun refreshKeepsTheRefreshToken() =
        runTest {
            val request = slot<InitiateAuthRequest>()
            coEvery { client.initiateAuth(capture(request)) } returns
                InitiateAuthResponse { authenticationResult = tokens(id = "id2", refresh = null) }

            val session = auth.refresh(Session("id", "access", "refresh", 1))

            assertEquals(Session("id2", "access", "refresh", NOW + EXPIRES_IN), session)
            assertEquals(AuthFlowType.RefreshTokenAuth, request.captured.authFlow)
            assertEquals(mapOf("REFRESH_TOKEN" to "refresh"), request.captured.authParameters)
        }

    /** チャレンジを求められたときは, 例外にする. */
    @Test
    fun signInFailsOnChallenge() =
        runTest {
            givenAuth(null)

            val error = assertFailsWith<IllegalStateException> { auth.signIn(EMAIL, "pass") }

            assertEquals("Cognito requires a challenge", error.message)
        }

    /** トークンの項目が, 応答にないときは, 例外にする. */
    @Test
    fun signInFailsWithoutTokens() =
        runTest {
            val cases =
                listOf(
                    tokens(id = null) to "ID token",
                    tokens(access = null) to "access token",
                    tokens(refresh = null) to "refresh token",
                )
            for ((result, name) in cases) {
                givenAuth(result)

                val error = assertFailsWith<IllegalStateException> { auth.signIn(EMAIL, "pass") }

                assertEquals("Cognito did not return the $name", error.message)
            }
        }

    /** 時計を渡さないときは, 実際の時計で, 期限を求める. */
    @Test
    fun signInUsesTheSystemClockByDefault() =
        runTest {
            coEvery { client.initiateAuth(any<InitiateAuthRequest>()) } returns
                InitiateAuthResponse { authenticationResult = tokens() }
            val before = System.currentTimeMillis() / 1000

            val session = UserPoolAuth(client, CLIENT_ID).signIn(EMAIL, "pass")

            assertTrue(session.expiresAt >= before + EXPIRES_IN)
        }
}
