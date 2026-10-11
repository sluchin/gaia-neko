/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import jp.gaianeko.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var capture: CaptureController? = null

    /**
     * 画面を作る.
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = ScrollView(this).apply { clipToPadding = false }
        root.applySystemBarInsets()
        setContentView(root)

        if (BuildConfig.IDENTITY_POOL_ID.isEmpty() || BuildConfig.BUCKET_NAME.isEmpty()) {
            root.showContent(TextView(this).apply { text = getString(R.string.not_configured) })
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
        root.showContent(screen.view)
    }

    /**
     * ログインしたあとの画面を表示する (撮影と, ログアウト. 管理者のマップは, これから).
     *
     * @param session トークン
     */
    private fun showHome(session: Session) {
        val authStack = checkNotNull(stack)
        val status = if (session.isAdmin) R.string.signed_in_admin else R.string.signed_in_user
        lateinit var screen: HomeScreen
        val controller =
            CaptureController(
                this,
                scope,
                authStack.uploader,
                { busy -> screen.setBusy(busy) },
                { text -> screen.showMessage(text) },
            )
        screen =
            HomeScreen(
                this,
                status,
                onCapture = controller::start,
                onSignOut = {
                    authStack.manager.signOut()
                    capture = null
                    showLogin()
                },
            )
        capture = controller
        root.showContent(screen.view)
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
     * 位置の権限の結果を, 撮影の流れに渡す.
     *
     * @param requestCode 要求の番号
     * @param permissions 権限
     * @param grantResults 権限ごとの結果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture?.onPermissionResult(requestCode, grantResults)
    }

    /**
     * カメラの結果を, 撮影の流れに渡す.
     *
     * @param requestCode 要求の番号
     * @param resultCode 結果
     * @param data カメラが返したデータ (使わない. 写真は, 渡したファイルに書かれる)
     */
    @Deprecated("The framework deprecated it, but the Activity API is enough for this screen")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        capture?.onCameraResult(requestCode, resultCode)
    }

    /** 画面を閉じるときに, 通信中の処理を止める. */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
