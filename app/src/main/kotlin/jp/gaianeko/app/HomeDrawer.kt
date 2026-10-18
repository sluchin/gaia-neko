/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.os.Build
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

/** ドロワーの見出しの余白 (dp). */
private const val HEADER_PADDING_DP = 24

/**
 * ログインしたあとの画面の, 左から引き出すメニュー (ナビゲーションドロワー).
 *
 * 上のバーの左のボタン (三本線) か, 画面の左端から引き出す. 戻るの操作で, 閉じる. 今は, 設定だけ.
 * ほかの画面 (ログイン, 地図) に切り替えるときは, [detach] で, 上のバーのボタンと, 戻るの操作を外す.
 *
 * @param activity 画面
 * @param content 中身 (下のタブの画面)
 * @param isAdmin 管理者か (見出しに, 役割を表示する)
 * @param onSettings 設定を開く
 */
class HomeDrawer(
    private val activity: AppCompatActivity,
    content: View,
    isAdmin: Boolean,
    onSettings: () -> Unit,
) {
    /** 画面をまとめた View. */
    val view = DrawerLayout(activity)

    private val navigation =
        NavigationView(activity).apply {
            addHeaderView(header(isAdmin))
            inflateMenu(R.menu.nav_drawer)
            setNavigationItemSelectedListener {
                view.closeDrawer(this)
                if (it.itemId == R.id.nav_settings) {
                    onSettings()
                }
                true
            }
        }
    private val toggle =
        ActionBarDrawerToggle(activity, view, R.string.drawer_open, R.string.drawer_close)
    private val back =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = view.closeDrawer(navigation)
        }

    init {
        view.addView(content)
        view.addView(
            navigation,
            DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.WRAP_CONTENT,
                DrawerLayout.LayoutParams.MATCH_PARENT,
                Gravity.START,
            ),
        )
        view.addDrawerListener(toggle)
        view.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    back.isEnabled = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    back.isEnabled = false
                }
            },
        )
        applyInsets()
        activity.onBackPressedDispatcher.addCallback(back)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toggle.syncState()
    }

    /**
     * 上のバーのボタンが押されたら, ドロワーを開け閉めする.
     *
     * @param item 押されたボタン
     * @return 処理したら, true
     */
    fun onOptionsItemSelected(item: MenuItem): Boolean = toggle.onOptionsItemSelected(item)

    /** 上のバーのボタンと, 戻るの操作を外す (ほかの画面に切り替えるとき). */
    fun detach() {
        back.remove()
        view.removeDrawerListener(toggle)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    /**
     * ドロワーを, 上のバーの下から始める (中身 ([HomeTabs]) と同じく, システムバーの余白を, 上に空ける).
     *
     * 画面は, 上のバーの下まで広がるので, 余白を空けないと, ドロワーの見出しが, 上のバーに隠れる.
     */
    private fun applyInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        navigation.setOnApplyWindowInsetsListener { target, insets ->
            val top = insets.getInsets(WindowInsets.Type.systemBars()).top
            val params = target.layoutParams as ViewGroup.MarginLayoutParams
            if (params.topMargin != top) {
                params.topMargin = top
                target.layoutParams = params
            }
            insets
        }
    }

    /**
     * ドロワーの見出し (アプリの名前と, 役割) を作る.
     *
     * @param isAdmin 管理者か
     * @return 見出し
     */
    private fun header(isAdmin: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = activity.dp(HEADER_PADDING_DP)
            setPadding(padding, padding, padding, padding)
            addView(
                activity
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.app_name) },
            )
            addView(
                activity.badgeText().apply {
                    setText(if (isAdmin) R.string.role_admin else R.string.role_user)
                },
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = activity.dp(HEADER_PADDING_DP / 2) },
            )
        }
}
