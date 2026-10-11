/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

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
    private val email = field(R.string.email, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val password = field(R.string.password, InputType.TYPE_TEXT_VARIATION_PASSWORD)
    private val code = field(R.string.code, InputType.TYPE_CLASS_NUMBER)
    private val signIn = button(R.string.sign_in) { onSignIn(emailText(), passwordText()) }
    private val signUp = button(R.string.sign_up) { onSignUp(emailText(), passwordText()) }
    private val confirm = button(R.string.confirm) { onConfirm(emailText(), code.text.toString()) }
    private val message = TextView(context)

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            listOf(email, password, signIn, signUp, code, confirm, message).forEach {
                addView(it)
            }
        }

    init {
        code.visibility = View.GONE
        confirm.visibility = View.GONE
    }

    /**
     * 通信している間は, ボタンを押せなくする.
     *
     * @param busy 通信している間は true
     */
    fun setBusy(busy: Boolean) {
        listOf(signIn, signUp, confirm).forEach { it.isEnabled = !busy }
    }

    /**
     * 画面の下に, メッセージを表示する.
     *
     * @param text メッセージ
     */
    fun showMessage(text: String) {
        message.text = text
    }

    /** 確認コードの入力欄と, 確認のボタンを出す (登録のあと). */
    fun showConfirm() {
        code.visibility = View.VISIBLE
        confirm.visibility = View.VISIBLE
        showMessage(context.getString(R.string.code_sent))
    }

    /**
     * 入力されたメールアドレス (前後の空白を除く).
     *
     * @return メールアドレス
     */
    private fun emailText(): String = email.text.toString().trim()

    /**
     * 入力されたパスワード.
     *
     * @return パスワード
     */
    private fun passwordText(): String = password.text.toString()

    /**
     * 入力欄を作る.
     *
     * @param hintId 案内の文字列のリソース
     * @param variation 入力の種類 (InputType)
     * @return 入力欄
     */
    private fun field(
        hintId: Int,
        variation: Int,
    ): EditText =
        EditText(context).apply {
            setHint(hintId)
            inputType =
                if (variation == InputType.TYPE_CLASS_NUMBER) {
                    variation
                } else {
                    InputType.TYPE_CLASS_TEXT or variation
                }
            setPadding(0, context.dp(GAP_DP), 0, context.dp(GAP_DP))
        }

    /**
     * ボタンを作る.
     *
     * @param textId 文字列のリソース
     * @param action 押されたときの処理
     * @return ボタン
     */
    private fun button(
        textId: Int,
        action: () -> Unit,
    ): Button =
        Button(context).apply {
            setText(textId)
            setOnClickListener { action() }
        }
}
