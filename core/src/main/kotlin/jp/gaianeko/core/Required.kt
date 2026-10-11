/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

/** ミリ秒を, 秒にするための値. */
internal const val MILLIS_PER_SECOND = 1000L

/**
 * Cognito の応答の項目が, あることを確かめる.
 *
 * @param value 応答の項目
 * @param name 項目の名前 (例外のメッセージに使う)
 * @return 項目の値
 * @throws IllegalStateException 項目がないとき
 */
internal fun <T : Any> required(
    value: T?,
    name: String,
): T = checkNotNull(value) { "Cognito did not return the $name" }
