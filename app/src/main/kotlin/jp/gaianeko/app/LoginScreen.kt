/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/** 見出しと, 入力欄の間の余白 (dp). */
private const val SECTION_GAP_DP = 32

/** 猫の顔 (アプリのアイコン) の大きさ (dp). */
private const val LOGO_DP = 96

/**
 * ログインの画面 (メールアドレスとパスワード. 登録のあとは, 確認コード).
 *
 * 押されたときの処理は, 呼び出し元が, コールバックで決める (ここは, 表示だけ).
 *
 * @property context コンテキスト
 * @property onSignIn ログインを押したとき (メールアドレス, パスワード)
 * @property onSignUp 登録を押したとき (メールアドレス, パスワード)
 * @property onConfirm 確認を押したとき (メールアドレス, 確認コード)
 */
class LoginScreen(
    private val context: Context,
    private val onSignIn: (String, String) -> Unit,
    private val onSignUp: (String, String) -> Unit,
    private val onConfirm: (String, String) -> Unit,
) {
    private val email =
        context.inputField(
            R.string.email,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        )
    private val password =
        context.inputField(
            R.string.password,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
    private val code = context.inputField(R.string.code, InputType.TYPE_CLASS_NUMBER)
    private val signIn =
        context.materialButton(R.string.sign_in) { onSignIn(emailText(), passwordText()) }
    private val signUp =
        context.materialButton(
            R.string.sign_up,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ) { onSignUp(emailText(), passwordText()) }
    private val confirm =
        context.materialButton(R.string.confirm) {
            onConfirm(emailText(), code.second.text.toString())
        }
    private val busy = context.busyIndicator()
    private val message = context.cardText()

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(busy)
            addView(header())
            addSpaced(email.first, SECTION_GAP_DP)
            addSpaced(password.first, GAP_DP)
            addSpaced(signIn, GAP_DP)
            addSpaced(signUp, GAP_DP / 2)
            addSpaced(code.first, SECTION_GAP_DP)
            addSpaced(confirm, GAP_DP)
            addSpaced(message, GAP_DP)
        }

    init {
        code.first.isVisible = false
        confirm.isVisible = false
    }

    /**
     * 通信している間は, ボタンを押せなくして, 進み具合を出す.
     *
     * @param busy 通信している間は true
     */
    fun setBusy(busy: Boolean) {
        listOf(signIn, signUp, confirm).forEach { it.isEnabled = !busy }
        this.busy.isVisible = busy
    }

    /**
     * 画面の下に, メッセージを表示する.
     *
     * @param text メッセージ
     */
    fun showMessage(text: String) {
        message.showOrHide(text)
    }

    /** 確認コードの入力欄と, 確認のボタンを出す (登録のあと). */
    fun showConfirm() {
        code.first.isVisible = true
        confirm.isVisible = true
        showMessage(context.getString(R.string.code_sent))
    }

    /**
     * 見出し (猫の顔, アプリの名前, 一言の説明) を作る.
     *
     * @return 見出し
     */
    private fun header(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addSpaced(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_launcher_foreground)
                    setBackgroundResource(R.drawable.logo_background)
                    importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                GAP_DP,
                context.dp(LOGO_DP),
                context.dp(LOGO_DP),
            )
            addSpaced(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.app_name) },
                GAP_DP,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            addSpaced(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ).apply { setText(R.string.tagline) },
                GAP_DP / 2,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    /**
     * 入力されたメールアドレス (前後の空白を除く).
     *
     * @return メールアドレス
     */
    private fun emailText(): String =
        email.second.text
            .toString()
            .trim()

    /**
     * 入力されたパスワード.
     *
     * @return パスワード
     */
    private fun passwordText(): String = password.second.text.toString()
}
