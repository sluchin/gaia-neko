/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import jp.gaianeko.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 画面の端と, 内容の間の余白 (dp). */
private const val CONTENT_PADDING_DP = 16

/**
 * 最初の画面 (ログインの状態で, 画面を切り替える).
 *
 * AWS の設定がなければ, その旨を表示する. 保存した更新トークンがあれば, ログインの画面を飛ばす.
 * ログインしたあとの画面 (撮影, 管理者のマップ) は, これから作る.
 */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var root: ScrollView
    private var stack: AuthStack? = null
    private var login: LoginScreen? = null

    /**
     * 画面を作る.
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = ScrollView(this).apply { clipToPadding = false }
        applySystemBarInsets()
        setContentView(root)

        if (BuildConfig.IDENTITY_POOL_ID.isEmpty() || BuildConfig.CLIENT_ID.isEmpty()) {
            show(TextView(this).apply { text = getString(R.string.not_configured) })
            return
        }
        val authStack = AuthStack(this).also { stack = it }
        scope.launch {
            val restored =
                runCatching { withContext(Dispatchers.IO) { authStack.manager.restore() } }
            val session = restored.getOrNull()
            if (session ==
                null
            ) {
                showLogin(restored.exceptionOrNull()?.message)
            } else {
                showHome(session)
            }
        }
    }

    /**
     * ログインの画面を表示する.
     *
     * @param error 前の処理の失敗のメッセージ (なければ, null)
     */
    private fun showLogin(error: String? = null) {
        val screen =
            LoginScreen(
                this,
                onSignIn = { email, password -> run(signIn(email, password)) },
                onSignUp = { email, password -> run(signUp(email, password)) },
                onConfirm = { email, code -> run(confirm(email, code)) },
            )
        login = screen
        error?.let { screen.showMessage(getString(R.string.error, it)) }
        show(screen.view)
    }

    /**
     * ログインしたあとの画面を表示する (今は, ログインの状態だけ).
     *
     * @param session トークン
     */
    private fun showHome(session: Session) {
        val status = if (session.isAdmin) R.string.signed_in_admin else R.string.signed_in_user
        show(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = getString(status) })
                addView(
                    Button(context).apply {
                        setText(R.string.sign_out)
                        setOnClickListener {
                            stack?.manager?.signOut()
                            showLogin()
                        }
                    },
                )
            },
        )
    }

    /**
     * 通信の処理を実行する (実行している間は, ボタンを押せなくして, 失敗したら, メッセージを表示する).
     *
     * @param action 通信の処理 (IO のスレッドで実行する)
     */
    private fun run(action: suspend () -> Unit) {
        val screen = login ?: return
        screen.setBusy(true)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure { screen.showMessage(getString(R.string.error, it.message)) }
            screen.setBusy(false)
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
            val session = checkNotNull(stack).manager.signIn(email, password)
            withContext(Dispatchers.Main) { showHome(session) }
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
            checkNotNull(stack).auth.signUp(email, password)
            withContext(Dispatchers.Main) { login?.showConfirm() }
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
            checkNotNull(stack).auth.confirmSignUp(email, code)
            withContext(Dispatchers.Main) { login?.showMessage(getString(R.string.confirmed)) }
        }

    /**
     * 画面の中身を差し替える.
     *
     * @param content 表示する View
     */
    private fun show(content: View) {
        root.removeAllViews()
        root.addView(content)
    }

    /**
     * 内容が, システムバー (ステータスバーなど) と, アクションバーの下に, 隠れないようにする.
     *
     * Android 15 以降 (targetSdk 35 以上) は, 画面が, システムバーの下まで広がる (edge-to-edge).
     * システムバーの余白と, アクションバーの高さを, パディングに入れる. それ以前の版は, 広がらないので,
     * 何もしない.
     */
    private fun applySystemBarInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val padding = dp(CONTENT_PADDING_DP)
        val actionBarSize =
            TypedValue().let {
                theme.resolveAttribute(android.R.attr.actionBarSize, it, true)
                TypedValue.complexToDimensionPixelSize(it.data, resources.displayMetrics)
            }
        root.setOnApplyWindowInsetsListener { target, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(
                bars.left + padding,
                bars.top + actionBarSize + padding,
                bars.right + padding,
                bars.bottom + padding,
            )
            insets
        }
    }

    /** 画面を閉じるときに, 通信中の処理を止める. */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
