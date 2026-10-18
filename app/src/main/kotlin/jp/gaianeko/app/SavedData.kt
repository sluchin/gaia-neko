/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import java.io.File

/**
 * 端末に保存した, 里親募集のデータ (猫の一覧と, 写真) の, 容量と削除 (設定の画面から使う).
 *
 * 写真のフォルダは, 消さずに, 中のファイルだけを消す (画像の読み込み ([ImageLoader]) が, 作ったフォルダに,
 * そのまま書き続けるため).
 */
object SavedData {
    /**
     * 使っている容量を返す (IO のスレッドで呼ぶ).
     *
     * @param context コンテキスト
     * @return 容量 (バイト)
     */
    fun size(context: Context): Long = files(context).sumOf { it.length() }

    /**
     * 削除する (IO のスレッドで呼ぶ).
     *
     * @param context コンテキスト
     */
    fun clear(context: Context) {
        files(context).forEach { it.delete() }
    }

    /**
     * 保存したファイルを返す (猫の一覧と, 写真).
     *
     * @param context コンテキスト
     * @return ファイル (ないものは, 含まない)
     */
    private fun files(context: Context): List<File> {
        val images =
            File(
                context.cacheDir,
                ADOPTION_IMAGES_DIR,
            ).listFiles().orEmpty().filter { it.isFile }
        return (images + File(context.filesDir, ADOPTION_CATS_FILE)).filter { it.exists() }
    }
}
