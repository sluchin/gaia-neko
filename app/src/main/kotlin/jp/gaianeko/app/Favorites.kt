/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import jp.gaianeko.core.AdoptionCat
import jp.gaianeko.core.isAdoptionOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

/** ログのタグ. */
private const val TAG = "Favorites"

/** 募集が終わった (譲渡済み) お気に入りの URL を保存するファイル (アプリのデータのフォルダの中) の名前. */
private const val CLOSED_FILE = "adoption-favorites-closed.json"

/**
 * お気に入りの猫 (里親募集のカードの, ハートのボタンで足し引きする. 新しく足した猫が, 先頭).
 *
 * アプリのデータに保存する ([ADOPTION_FAVORITES_FILE]). 猫の情報 (名前, 写真の URL など) ごと保存するので,
 * 里親募集の一覧から消えても, お気に入りには残る. 設定の「保存したデータを削除」では, 消さない.
 *
 * 起動してから初めて, お気に入りのタブを開いたときに ([checkOnce]), 猫ごとに, サイトの詳しいページを見て,
 * 募集が終わった (譲渡済み) かを確かめて, 保存する ([CLOSED_FILE]).
 *
 * @param context コンテキスト
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 */
class Favorites(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = CatStore(context, ADOPTION_FAVORITES_FILE)
    private val closedFile = File(context.filesDir, CLOSED_FILE)
    private val list = mutableListOf<AdoptionCat>()
    private val closed = mutableSetOf<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private var checked = false

    // 書く順番を守るため, 1 つずつ書く
    private val writer = Dispatchers.IO.limitedParallelism(1)

    /** お気に入りの猫 (新しく足した順. 一覧のアダプタが, そのまま使う). */
    val cats: List<AdoptionCat> = list

    private val loading =
        scope.launch {
            val saved = withContext(writer) { store.read() }.orEmpty()
            val savedClosed = withContext(writer) { readClosed() }
            // 読み終える前に, 足した猫があれば, 先頭に残す
            val urls = list.mapTo(HashSet()) { it.url }
            list += saved.filter { it.url !in urls }
            closed += savedClosed
            changed()
        }

    /**
     * お気に入りかを返す.
     *
     * @param url 猫の詳しいページの URL
     * @return お気に入りなら, true
     */
    fun contains(url: String): Boolean = list.any { it.url == url }

    /**
     * 募集が終わった (譲渡済み) お気に入りかを返す.
     *
     * @param url 猫の詳しいページの URL
     * @return 募集が終わっていれば, true (確かめていないか, 募集していれば, false)
     */
    fun isClosed(url: String): Boolean = url in closed

    /**
     * お気に入りに足す (お気に入りなら, 外す). 保存して, 変わったことを知らせる.
     *
     * @param cat 猫
     */
    fun toggle(cat: AdoptionCat) {
        if (list.removeAll { it.url == cat.url }) {
            closed -= cat.url
            AppLog.d(TAG) { "Removed a favorite: ${cat.url}" }
        } else {
            list.add(0, cat)
            AppLog.d(TAG) { "Added a favorite: ${cat.url}" }
        }
        changed()
        save()
    }

    /**
     * 起動してから初めてのときだけ, お気に入りの猫が, まだ募集しているかを, サイトで確かめる
     * (猫ごとに, 1 匹ずつ, 詳しいページを取得する).
     *
     * 詳しいページがない (404, 410) か, 状態が「里親募集中」でなければ, 募集が終わった (譲渡済み) とする.
     * 通信に失敗した猫は, 前のまま.
     */
    fun checkOnce() {
        if (checked) {
            return
        }
        checked = true
        scope.launch {
            loading.join()
            for (cat in list.toList()) {
                val open = withContext(Dispatchers.IO) { isOpen(cat.url) } ?: continue
                AppLog.d(TAG) { "${cat.url}: ${if (open) "open" else "closed"}" }
                val changed = if (open) closed.remove(cat.url) else closed.add(cat.url)
                if (changed) {
                    changed()
                }
            }
            AppLog.i(TAG, "Checked ${list.size} favorites, ${closed.size} closed")
            save()
        }
    }

    /**
     * 変わったときに呼ぶ処理を, 足す (画面のスレッドで呼ぶ).
     *
     * @param listener 処理
     */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** 変わったことを, 知らせる. */
    private fun changed() = listeners.forEach { it() }

    /** お気に入りと, 募集が終わった猫を, 保存する. */
    private fun save() {
        val cats = list.toList()
        val urls = closed.filter { url -> cats.any { it.url == url } }
        scope.launch {
            withContext(writer) {
                store.write(cats)
                closedFile.writeText(JSONArray(urls).toString())
            }
        }
    }

    /**
     * 保存した, 募集が終わった猫の URL を読む (IO のスレッドで呼ぶ).
     *
     * @return URL (保存していないか, 読めなければ, 空)
     */
    private fun readClosed(): List<String> {
        if (!closedFile.exists()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(closedFile.readText())
            List(array.length()) { array.getString(it) }
        } catch (e: JSONException) {
            AppLog.w(TAG, "Could not read $CLOSED_FILE", e)
            emptyList()
        }
    }

    /**
     * 猫の詳しいページを取得して, まだ募集しているかを返す (IO のスレッドで呼ぶ).
     *
     * @param url 詳しいページの URL
     * @return 募集していれば, true. 終わっていれば, false. 通信に失敗したら, null
     */
    private fun isOpen(url: String): Boolean? =
        try {
            isAdoptionOpen(String(fetchBytes(url), Charsets.UTF_8))
        } catch (e: HttpStatusException) {
            val gone =
                e.code == HttpURLConnection.HTTP_NOT_FOUND || e.code == HttpURLConnection.HTTP_GONE
            AppLog.d(TAG) { "$url: HTTP ${e.code}" }
            if (gone) false else null
        } catch (e: IOException) {
            AppLog.w(TAG, "Could not check a favorite", e)
            null
        }
}
