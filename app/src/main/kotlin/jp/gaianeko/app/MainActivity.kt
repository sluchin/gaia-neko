/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import jp.gaianeko.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ログのタグ. */
private const val TAG = "Session"

/** 設定の画面を開く要求の番号 (撮影の流れの番号と, 重ならないように). */
private const val REQUEST_SETTINGS = 10

/**
 * 最初の画面 (ログインの状態で, 画面を切り替える).
 *
 * AWS の設定がなければ, その旨を表示する. 保存した更新トークンがあれば, ログインの画面を飛ばす.
 * ログインしたあとは, 下のタブで, 猫情報提供 (撮影) と, 里親募集と, お気に入りの画面を切り替える. 管理者には, 地図の画面へのボタンも出る.
 * 左から引き出すメニュー ([HomeDrawer]) から, 設定の画面 ([SettingsActivity]) を開く.
 */
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var root: ScrollView
    private var stack: AuthStack? = null
    private var login: LoginController? = null
    private var capture: CaptureController? = null
    private var map: AdminMapScreen? = null
    private var drawer: HomeDrawer? = null
    private val images by lazy { ImageLoader(this) }
    private val favorites by lazy { Favorites(this, scope) }
    private val adoption by lazy { AdoptionScreen(this, scope, images, favorites) }
    private val favoritesScreen by lazy { FavoritesScreen(this, images, favorites) }

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
            root.showContent(cardText().apply { showOrHide(getString(R.string.not_configured)) })
            return
        }
        val authStack = AuthStack(this).also { stack = it }
        val controller =
            LoginController(this, scope, authStack, ::showInRoot) { showHome(it) }
                .also { login = it }
        scope.launch {
            val restored =
                runCatching { withContext(Dispatchers.IO) { authStack.manager.restore() } }
            val session = restored.getOrNull()
            restored.exceptionOrNull()?.let { AppLog.w(TAG, "Could not restore the session", it) }
            AppLog.d(TAG) { "Restored the session: ${session != null}" }
            if (session == null) {
                controller.showLogin(restored.exceptionOrNull()?.message)
            } else {
                showHome(session)
            }
        }
    }

    /**
     * スクロールする画面 (ログインの画面など) に, 中身を表示する.
     *
     * @param content 表示する View
     */
    private fun showInRoot(content: View) {
        drawer?.detach()
        drawer = null
        if (root.parent == null) {
            setContentView(root)
        }
        root.showContent(content)
    }

    /**
     * ログインしたあとの画面を表示する (下のタブで, 猫情報提供 (撮影) と, 里親募集と, お気に入り. 管理者には, 地図のボタンも).
     *
     * @param session トークン
     */
    private fun showHome(session: Session) {
        val authStack = checkNotNull(stack)
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
                session.isAdmin,
                onCapture = controller::start,
                onMap = { showMap(session) },
                onSignOut = {
                    authStack.manager.signOut()
                    capture = null
                    login?.showLogin()
                },
            )
        capture = controller
        drawer?.detach()
        val home =
            HomeDrawer(
                this,
                HomeTabs(this, screen.view, adoption, favorites, favoritesScreen.view).view,
                session.isAdmin,
            ) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(this, SettingsActivity::class.java), REQUEST_SETTINGS)
            }
        drawer = home
        setContentView(home.view)
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
                showHome(session)
            }
        map = screen
        drawer?.detach()
        drawer = null
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
     * カメラの結果を, 撮影の流れに渡す (設定の画面から戻ったときは, 保存したデータを削除したかを見る).
     *
     * @param requestCode 要求の番号
     * @param resultCode 結果
     * @param data 返したデータ (使わない. 写真は, 渡したファイルに書かれる)
     */
    @Deprecated("The framework deprecated it, but the Activity API is enough for this screen")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            // 設定で, 保存したデータを削除した
            adoption.forget()
            images.clearMemory()
        }
        capture?.onCameraResult(requestCode, resultCode)
    }

    /**
     * 上のバーのボタン (ドロワーを開け閉めする三本線) を処理する.
     *
     * @param item 押されたボタン
     * @return 処理したら, true
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        drawer?.onOptionsItemSelected(item) == true || super.onOptionsItemSelected(item)

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
        images.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
