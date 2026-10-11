/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * 最初の画面 (雛形. 撮影, ログイン, 管理者のマップは, これから作る).
 */
class MainActivity : Activity() {
    /**
     * 画面を作る (AWS の設定がないときは, その旨を表示する).
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text =
                    if (BuildConfig.IDENTITY_POOL_ID.isEmpty()) {
                        getString(R.string.not_configured)
                    } else {
                        getString(R.string.app_name)
                    }
            },
        )
    }
}
