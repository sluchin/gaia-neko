/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ログインしたあとの画面 (撮影のボタンと, ログアウトのボタン).
 *
 * @param context コンテキスト
 * @param statusId 最初に表示する状態の文字列のリソース
 * @param onCapture 撮影を押したとき
 * @param onMap 地図を押したとき (管理者だけ. 管理者でなければ, null で, ボタンを出さない)
 * @param onSignOut ログアウトを押したとき
 */
class HomeScreen(
    context: Context,
    statusId: Int,
    onCapture: () -> Unit,
    onMap: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    private val status = TextView(context).apply { setText(statusId) }
    private val capture =
        Button(context).apply {
            setText(R.string.take_photo)
            setOnClickListener { onCapture() }
        }
    private val message = TextView(context)

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(capture)
            onMap?.let {
                addView(
                    Button(context).apply {
                        setText(R.string.open_map)
                        setOnClickListener { it() }
                    },
                )
            }
            addView(message)
            addView(
                Button(context).apply {
                    setText(R.string.sign_out)
                    setOnClickListener { onSignOut() }
                },
            )
        }

    /**
     * 画面の下に, メッセージを表示する.
     *
     * @param text メッセージ
     */
    fun showMessage(text: String) {
        message.text = text
    }

    /**
     * 通信している間は, 撮影のボタンを押せなくする.
     *
     * @param busy 通信している間は true
     */
    fun setBusy(busy: Boolean) {
        capture.isEnabled = !busy
    }
}
