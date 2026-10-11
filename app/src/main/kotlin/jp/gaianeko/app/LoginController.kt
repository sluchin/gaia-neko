/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.Activity
import android.view.View
import jp.gaianeko.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ログインの流れ (ログインの画面を出して, 登録, 確認, ログインを実行する).
 *
 * @property activity 画面
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @property stack ログインに使うもの
 * @property show 画面の中身を差し替える関数
 * @property onSignedIn ログインできたとき
 */
class LoginController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val stack: AuthStack,
    private val show: (View) -> Unit,
    private val onSignedIn: (Session) -> Unit,
) {
    private var screen: LoginScreen? = null

    /**
     * ログインの画面を表示する.
     *
     * @param error 前の処理の失敗のメッセージ (なければ, null)
     */
    fun showLogin(error: String? = null) {
        val login =
            LoginScreen(
                activity,
                onSignIn = { email, password -> run(signIn(email, password)) },
                onSignUp = { email, password -> run(signUp(email, password)) },
                onConfirm = { email, code -> run(confirm(email, code)) },
            )
        screen = login
        error?.let { login.showMessage(activity.getString(R.string.error, it)) }
        show(login.view)
    }

    /**
     * 通信の処理を実行する (実行している間は, ボタンを押せなくして, 失敗したら, メッセージを表示する).
     *
     * @param action 通信の処理 (IO のスレッドで実行する)
     */
    private fun run(action: suspend () -> Unit) {
        val login = screen ?: return
        login.setBusy(true)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure { login.showMessage(activity.getString(R.string.error, it.message)) }
            login.setBusy(false)
        }
    }

    /**
     * ログインする (成功したら, ログインしたあとの画面にする).
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return 通信の処理
     */
    private fun signIn(
        email: String,
        password: String,
    ): suspend () -> Unit =
        {
            val session = stack.manager.signIn(email, password)
            withContext(Dispatchers.Main) { onSignedIn(session) }
        }

    /**
     * 登録する (成功したら, 確認コードの入力欄を出す).
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return 通信の処理
     */
    private fun signUp(
        email: String,
        password: String,
    ): suspend () -> Unit =
        {
            stack.auth.signUp(email, password)
            withContext(Dispatchers.Main) { screen?.showConfirm() }
        }

    /**
     * 確認コードで登録を完了する (成功したら, そのメッセージを表示する. ログインは, 続けて押す).
     *
     * @param email メールアドレス
     * @param code 確認コード
     * @return 通信の処理
     */
    private fun confirm(
        email: String,
        code: String,
    ): suspend () -> Unit =
        {
            stack.auth.confirmSignUp(email, code)
            withContext(
                Dispatchers.Main,
            ) { screen?.showMessage(activity.getString(R.string.confirmed)) }
        }
}
