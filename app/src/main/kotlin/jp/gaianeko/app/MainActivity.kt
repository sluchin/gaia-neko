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
 * ログインしたあとは, 撮影の画面になる. 管理者には, 地図の画面へのボタンも出る.
 */
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var root: ScrollView
    private var stack: AuthStack? = null
    private var login: LoginController? = null
    private var capture: CaptureController? = null
    private var map: AdminMapScreen? = null

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
        val controller =
            LoginController(this, scope, authStack, root::showContent) { showHome(it) }
                .also { login = it }
        scope.launch {
            val restored =
                runCatching { withContext(Dispatchers.IO) { authStack.manager.restore() } }
            val session = restored.getOrNull()
            if (session == null) {
                controller.showLogin(restored.exceptionOrNull()?.message)
            } else {
                showHome(session)
            }
        }
    }

    /**
     * ログインしたあとの画面を表示する (撮影と, ログアウト. 管理者には, 地図のボタンも).
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
                onMap = if (session.isAdmin) ({ showMap(session) }) else null,
                onSignOut = {
                    authStack.manager.signOut()
                    capture = null
                    login?.showLogin()
                },
            )
        capture = controller
        root.showContent(screen.view)
    }

    /**
     * 管理者の地図の画面を表示する (閉じると, ログインしたあとの画面に戻る).
     *
     * 地図は, スクロールする画面 (ScrollView) の中に置くと, 高さが 0 になって, 表示されない. また, 地図の
     * 操作 (ドラッグ) と, 画面のスクロールが, 競合する. そのため, 画面全体を, 地図の画面に差し替える.
     *
     * @param session トークン
     */
    private fun showMap(session: Session) {
        val authStack = checkNotNull(stack)
        val screen =
            AdminMapScreen(this, scope, authStack.sightings, authStack.photos, authStack.remover) {
                map?.onDestroy()
                map = null
                setContentView(root)
                showHome(session)
            }
        map = screen
        screen.view.applySystemBarInsets()
        setContentView(screen.view)
        screen.load()
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

    /** 画面が前面に出たとき (地図を再開する). */
    override fun onResume() {
        super.onResume()
        map?.onResume()
    }

    /** 画面が背面に回ったとき (地図を止める). */
    override fun onPause() {
        map?.onPause()
        super.onPause()
    }

    /** 画面を閉じるときに, 通信中の処理を止める. */
    override fun onDestroy() {
        map?.onDestroy()
        scope.cancel()
        super.onDestroy()
    }
}
