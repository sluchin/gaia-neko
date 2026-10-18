/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 接続と読み込みの待ち時間 (ミリ秒). */
private const val TIMEOUT_MS = 15_000

/** サイトに名乗る User-Agent (どのアプリからの取得かを, サイトの人が分かるように). */
private const val USER_AGENT = "gaia-neko/0.1 (+https://github.com/sluchin/gaia-neko)"

/**
 * サイトが, 200 以外の応答を返したとき.
 *
 * @property code 応答の番号 (404 など)
 */
class HttpStatusException(
    val code: Int,
) : IOException("HTTP $code")

/**
 * サイトから, URL の中身を取得する (IO のスレッドで呼ぶ. 同じサイトの中の転送には, ついていく).
 *
 * @param url 取得する URL
 * @return 中身
 * @throws IOException 取得できないとき (通信の失敗. 200 以外の応答は, [HttpStatusException])
 */
fun fetchBytes(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    connection.setRequestProperty("User-Agent", USER_AGENT)
    return try {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw HttpStatusException(code)
        }
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

/**
 * URL を, 端末のキャッシュのファイルの名前にする (URL の SHA-256 の, 16 進数).
 *
 * @param url URL
 * @return ファイルの名前
 */
fun cacheName(url: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
