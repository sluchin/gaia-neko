/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.view.isVisible

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/** 見出しと, 撮影のボタンの間の余白 (dp). */
private const val SECTION_GAP_DP = 32

/** 撮影のボタンの高さ (dp. 外で, 片手でも押しやすいように, 大きくする). */
private const val CAPTURE_HEIGHT_DP = 72

/**
 * ログインしたあとの画面 (撮影のボタンと, ログアウトのボタン. 管理者には, 地図のボタンも).
 *
 * @param context コンテキスト
 * @param isAdmin 管理者か (役割の印と, 地図のボタンを出し分ける)
 * @param onCapture 撮影を押したとき
 * @param onMap 地図を押したとき (管理者だけに, ボタンを出す)
 * @param onSignOut ログアウトを押したとき
 */
class HomeScreen(
    context: Context,
    isAdmin: Boolean,
    onCapture: () -> Unit,
    onMap: () -> Unit,
    onSignOut: () -> Unit,
) {
    private val capture =
        context
            .materialButton(R.string.take_photo, iconId = R.drawable.ic_camera) { onCapture() }
            .apply {
                // 文字の見た目を変えると, 色も変わるので, ボタンの色 (押せないときの色を含む) に戻す
                val colors = textColors
                setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                )
                setTextColor(colors)
            }
    private val busy = context.busyIndicator()
    private val message =
        context.cardText().apply {
            showOrHide(
                context.getString(
                    if (isAdmin) R.string.signed_in_admin else R.string.signed_in_user,
                ),
            )
        }

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(busy)
            addSpaced(header(context, isAdmin), GAP_DP)
            addSpaced(capture, SECTION_GAP_DP, height = context.dp(CAPTURE_HEIGHT_DP))
            addSpaced(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ).apply {
                        setText(R.string.capture_note)
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                GAP_DP / 2,
            )
            if (isAdmin) {
                addSpaced(
                    context.materialButton(
                        R.string.open_map,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                        R.drawable.ic_map,
                    ) { onMap() },
                    GAP_DP,
                )
            }
            addSpaced(message, SECTION_GAP_DP)
            addSpaced(
                context.materialButton(
                    R.string.sign_out,
                    androidx.appcompat.R.attr.borderlessButtonStyle,
                    R.drawable.ic_logout,
                ) { onSignOut() },
                SECTION_GAP_DP,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

    /**
     * 画面の下に, メッセージを表示する.
     *
     * @param text メッセージ
     */
    fun showMessage(text: String) {
        message.showOrHide(text)
    }

    /**
     * 通信している間は, 撮影のボタンを押せなくして, 進み具合を出す.
     *
     * @param busy 通信している間は true
     */
    fun setBusy(busy: Boolean) {
        capture.isEnabled = !busy
        this.busy.isVisible = busy
    }

    /**
     * 見出し (アプリの名前と, 役割の印) を作る.
     *
     * @param context コンテキスト
     * @param isAdmin 管理者か
     * @return 見出し
     */
    private fun header(
        context: Context,
        isAdmin: Boolean,
    ): LinearLayout =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.app_name) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                context.badgeText().apply {
                    setText(if (isAdmin) R.string.role_admin else R.string.role_user)
                },
            )
        }
}
