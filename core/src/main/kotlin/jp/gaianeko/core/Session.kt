/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import java.util.Base64

/** 管理者のグループの名前 (Cognito のユーザープールのグループ). */
const val ADMIN_GROUP = "admin"

/** ID トークンの, グループの項目 (cognito:groups) を探す式. */
private val GROUPS_PATTERN = Regex("\"cognito:groups\"\\s*:\\s*\\[([^\\]]*)]")

/** グループの一覧の中の, 引用符で囲んだ名前を探す式. */
private val QUOTED_NAME_PATTERN = Regex("\"([^\"]*)\"")

/**
 * ログインした利用者の, Cognito のトークン.
 *
 * @property idToken ID トークン (ID プールの認証情報の取得と, グループの判定に使う)
 * @property accessToken アクセストークン
 * @property refreshToken 更新トークン (ID トークンの期限が切れたときに, 取り直す)
 * @property expiresAt ID トークンの期限 (UNIX 時刻 (s))
 */
data class Session(
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
) {
    /**
     * 管理者のグループに入っているか (画面の出し分けだけに使う. 権限は, IAM のロールが決める).
     */
    val isAdmin: Boolean get() = ADMIN_GROUP in idTokenGroups(idToken)
}

/**
 * ID トークンから, 所属しているグループの名前を取り出す.
 *
 * ID トークンは, Cognito が返したもので, 署名は, 検証しない (画面の出し分けだけに使うため).
 * 形が違う, 読めない, グループの項目がないときは, 空の一覧にする.
 *
 * @param idToken ID トークン (JWT. ヘッダー.本体.署名)
 * @return グループの名前 (ないときは, 空)
 */
fun idTokenGroups(idToken: String): List<String> {
    val payload = idToken.split(".").getOrNull(1).orEmpty()
    val json =
        runCatching { String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8) }
            .getOrDefault("")
    val names =
        GROUPS_PATTERN
            .find(json)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    return QUOTED_NAME_PATTERN.findAll(names).map { it.groupValues[1] }.toList()
}
