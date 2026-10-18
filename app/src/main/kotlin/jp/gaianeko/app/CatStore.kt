/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import jp.gaianeko.core.AdoptionCat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** 猫の一覧を保存するファイル (アプリのデータのフォルダの中) の名前. */
internal const val ADOPTION_CATS_FILE = "adoption-cats.json"

/** お気に入りの猫を保存するファイル (アプリのデータのフォルダの中) の名前. */
internal const val ADOPTION_FAVORITES_FILE = "adoption-favorites.json"

/** 前の版が, 一覧のページ (HTML) を保存したフォルダの名前 (もう使わない). */
private const val OLD_DIR_NAME = "adoption-pages"

/**
 * 里親募集の猫の一覧 (または, お気に入り) を, アプリのデータに保存する (JSON. 写真は, [ImageLoader] が
 * 別に保存する).
 *
 * キャッシュのフォルダ ([Context.getCacheDir]) は, 端末の空きが少ないと, システムに消されるので,
 * アプリのデータのフォルダ ([Context.getFilesDir]) に保存する.
 *
 * @param context コンテキスト
 * @param name ファイルの名前 (既定は, 里親募集の一覧)
 */
class CatStore(
    context: Context,
    name: String = ADOPTION_CATS_FILE,
) {
    private val file = File(context.filesDir, name)

    init {
        // 前の版が保存した, 一覧のページ (HTML) は, もう使わない.
        File(context.filesDir, OLD_DIR_NAME).deleteRecursively()
        File(context.cacheDir, OLD_DIR_NAME).deleteRecursively()
    }

    /**
     * 保存した一覧を読む (IO のスレッドで呼ぶ).
     *
     * @return 猫. 保存していないか, 読めなければ, null
     */
    @Suppress("SwallowedException")
    fun read(): List<AdoptionCat>? {
        if (!file.exists()) {
            return null
        }
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { toCat(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * 一覧を保存する (IO のスレッドで呼ぶ. 書きかけで止まっても, 前の一覧が残るように, 別のファイルに
     * 書いてから, 置き換える).
     *
     * @param cats 猫
     */
    fun write(cats: List<AdoptionCat>) {
        val array = JSONArray()
        cats.forEach { array.put(toJson(it)) }
        val temp = File(file.path + ".tmp")
        temp.writeText(array.toString())
        temp.renameTo(file)
    }

    /**
     * 猫を, JSON にする (値のない項目は, 書かない).
     *
     * @param cat 猫
     * @return JSON
     */
    private fun toJson(cat: AdoptionCat): JSONObject =
        JSONObject()
            .put("name", cat.name)
            .put("age", cat.age)
            .put("sex", cat.sex)
            .put("status", cat.status)
            .put("url", cat.url)
            .put("imageUrl", cat.imageUrl)

    /**
     * JSON を, 猫にする.
     *
     * @param json JSON
     * @return 猫
     * @throws JSONException 名前か, URL がないとき
     */
    private fun toCat(json: JSONObject): AdoptionCat =
        AdoptionCat(
            name = json.getString("name"),
            age = json.stringOrNull("age"),
            sex = json.stringOrNull("sex"),
            status = json.stringOrNull("status"),
            url = json.getString("url"),
            imageUrl = json.stringOrNull("imageUrl"),
        )

    /**
     * 項目の文字を返す.
     *
     * @receiver JSON
     * @param key 項目の名前
     * @return 文字. 項目がなければ, null
     */
    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key)) getString(key) else null
}
