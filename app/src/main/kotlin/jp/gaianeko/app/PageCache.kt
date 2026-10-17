/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 端末に残したページを, 取得し直さずに使う時間 (時間). */
private const val MAX_AGE_HOURS = 6L

/** 端末に残したページを, 取得し直さずに使う時間 (ミリ秒). */
private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(MAX_AGE_HOURS)

/**
 * サイトのページ (里親募集の一覧の HTML) を, 端末のキャッシュに残して, 取得の回数を減らす.
 *
 * 残したページが [MAX_AGE_HOURS] 時間より新しければ, 通信しない. 取得に失敗したときは, 古くても,
 * 残したページを使う (通信できないときも, 前に見た一覧を表示する).
 *
 * @param context コンテキスト
 */
class PageCache(
    context: Context,
) {
    private val dir = File(context.cacheDir, "adoption-pages").apply { mkdirs() }

    /**
     * ページを返す (IO のスレッドで呼ぶ).
     *
     * @param url ページの URL
     * @param useCache 新しいページが残っていれば, それを使うか (再読み込みのときは, false)
     * @return ページの中身
     * @throws IOException 取得できず, 残したページもないとき
     */
    fun get(
        url: String,
        useCache: Boolean,
    ): String {
        val file = File(dir, cacheName(url))
        val age = System.currentTimeMillis() - file.lastModified()
        if (useCache && file.exists() && age < MAX_AGE_MS) {
            return file.readText()
        }
        return try {
            fetchBytes(url).toString(Charsets.UTF_8).also { file.writeText(it) }
        } catch (e: IOException) {
            if (!file.exists()) {
                throw e
            }
            file.readText()
        }
    }
}
