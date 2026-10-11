/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

/**
 * 猫の目撃 (DynamoDB のテーブル gaia-neko-sightings の 1 件).
 *
 * @property id 目撃の識別子 (UUID. 利用者が撮影のときに決める. ソートキー)
 * @property userId 撮影した利用者の識別子 (Cognito の ID プールの ID. パーティションキー.
 * IAM の権限を, 自分の分だけに絞るため, ユーザープールの sub ではなく, この ID を使う)
 * @property point 撮影した位置
 * @property takenAt 撮影した時刻 (UNIX 時刻 (s))
 * @property photoKey 写真の, S3 のキー (写真の本体は, S3 に置く. DynamoDB の 1 件は, 400 KB まで)
 * @property photographer 撮影した利用者のメールアドレス (管理者が, 誰の投稿かを知るための表示用. 利用者が
 * 自分で書くので, 信頼できる識別は, userId. ないこともある)
 */
data class Sighting(
    val id: String,
    val userId: String,
    val point: GeoPoint,
    val takenAt: Long,
    val photoKey: String,
    val photographer: String? = null,
)

/**
 * 写真の, S3 のキーを作る (利用者ごとの接頭辞の下に置く. 利用者のロールの権限を, この接頭辞に絞るため).
 *
 * @param userId 利用者の識別子
 * @param sightingId 目撃の識別子
 * @return キー (photos/利用者の識別子/目撃の識別子.jpg)
 */
fun photoKey(
    userId: String,
    sightingId: String,
): String = "photos/$userId/$sightingId.jpg"
