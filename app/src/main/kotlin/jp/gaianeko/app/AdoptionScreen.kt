/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.gaianeko.core.ADOPTION_URL
import jp.gaianeko.core.AdoptionCat
import jp.gaianeko.core.AdoptionPage
import jp.gaianeko.core.newAdoptionCats
import jp.gaianeko.core.parseAdoptionPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** ログのタグ. */
private const val TAG = "Adoption"

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/**
 * 1 ページを, サイトから取得する.
 *
 * @param url ページの URL
 * @return ページ
 */
private suspend fun fetchPage(url: String): AdoptionPage =
    withContext(Dispatchers.IO) {
        parseAdoptionPage(String(fetchBytes(url), Charsets.UTF_8), url).also {
            AppLog.d(TAG) { "Fetched $url: ${it.cats.size} cats, total ${it.total}" }
        }
    }

/**
 * 里親募集の画面 (ニャン友ねっとわーく北海道の, 里親募集の一覧を, カードで並べる).
 *
 * 一覧は, [CatStore] で, アプリに保存する. 起動してから初めて開いたときは, 保存した一覧をすぐに表示して,
 * 裏で, サイトの最初のページから, 新しい猫 (保存した一覧にない猫) を探して, 一覧の先頭に足す. サイトは,
 * 新しい猫が先頭に来るので, 新しい猫のいないページまで来たら, 止める (普段は, 1 ページだけ取得する).
 * 更新 (上の更新のボタンか, 一覧を引き下げる) も, 新しい猫を探して, 先頭に足す. 保存した一覧がないときは, 最初のページから
 * 最後のページまで, 1 ページずつ順に取得して, 一覧を作る (取得している間も, 一覧を見られる).
 * 写真は, [ImageLoader] で, サイトから取得する. カードを押すと, サイトの詳しいページを, ブラウザで開く.
 * 仕様の全体は, `ADOPTION.md`.
 *
 * @property context コンテキスト (画面)
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @param images 写真の読み込み
 * @param favorites お気に入り (カードの, ハートのボタン)
 */
class AdoptionScreen(
    private val context: Context,
    private val scope: CoroutineScope,
    images: ImageLoader,
    favorites: Favorites,
) {
    private val count =
        context.styledText(
            com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )

    /**
     * 更新 (更新のボタンと, 引き下げ). 一覧があれば, 新しい猫を探して, 先頭に足す (探している間は, 引き下げの
     * 丸を回す. いなければ, そう表示する). 一覧がなければ (最初の取得に失敗したとき), 全てのページを取得する
     * (進み具合のバーを出す). 取得している間は, 何もしない.
     */
    private val refresh: () -> Unit = {
        if (job?.isActive == true) {
            swipe.isRefreshing = false
        } else {
            start {
                if (cats.isEmpty()) {
                    swipe.isRefreshing = false
                    loadAll()
                } else {
                    swipe.isRefreshing = true
                    addNew(manual = true)
                }
            }
        }
    }
    private val refreshButton =
        MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialIconButtonStyle,
        ).apply {
            setIconResource(R.drawable.ic_refresh)
            contentDescription = context.getString(R.string.refresh)
            tooltipText = contentDescription
            setOnClickListener { refresh() }
        }
    private val busy = context.busyIndicator()
    private val message = context.cardText()
    private val store = CatStore(context)
    private val cats = mutableListOf<AdoptionCat>()
    private val grid = context.catGrid(cats, images, favorites)
    private val adapter = grid.second
    private val swipe =
        SwipeRefreshLayout(context).apply {
            addView(grid.first)
            setColorSchemeColors(
                MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary),
            )
            setOnRefreshListener { refresh() }
        }
    private var loaded = false

    /** 取得している処理 (なければ, null). */
    private var job: Job? = null

    /** 一覧の, 全ての猫の数 (最初のページで分かる. 分からなければ, null). */
    private var total: Int? = null

    /** 画面をまとめた View. */
    val view =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(busy)
            addView(header())
            addView(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                    ).apply { setText(R.string.adoption_source) },
            )
            addSpaced(message, GAP_DP)
            addView(
                swipe,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = context.dp(GAP_DP)
                },
            )
        }

    init {
        // お気に入りが変わったら, ハートを描き直す
        favorites.addListener { adapter.notifyDataSetChanged() }
    }

    /**
     * 起動してから初めて表示するときだけ, 一覧を表示する (保存した一覧があれば, それと, サイトの
     * 新しい猫. なければ, サイトの全てのページ). 2 回目からと, 失敗したときは, 更新で.
     */
    fun loadOnce() {
        if (!loaded) {
            loaded = true
            start {
                val saved = withContext(Dispatchers.IO) { store.read() }
                AppLog.d(TAG) { "Saved cats: ${saved?.size}" }
                if (saved == null) {
                    loadAll()
                } else {
                    replace(saved)
                    addNew(manual = false)
                }
            }
        }
    }

    /**
     * 取得を始める (取得している間は, 更新のボタンを押せなくする. 失敗したら, メッセージを表示する.
     * バーは, 全てのページを取得するときだけ, [loadAll] が表示する. 新しい猫を探す間は, 保存した一覧を
     * 表示しているので, 表示しない).
     *
     * @param work 取得する処理
     */
    private fun start(work: suspend () -> Unit) {
        refreshButton.isEnabled = false
        message.showOrHide("")
        job =
            scope.launch {
                runCatching { work() }
                    .onFailure {
                        // 止めたときは, ここで終える ([forget] が, 画面を片付けている)
                        if (it is CancellationException) throw it
                        AppLog.w(TAG, "Could not fetch the adoption list", it)
                        message.showOrHide(context.getString(R.string.error, it.message))
                    }
                busy.isVisible = false
                swipe.isRefreshing = false
                refreshButton.isEnabled = true
                showCount(done = true)
            }
    }

    /**
     * 一覧を捨てる (設定で, 保存したデータを削除したとき. 取得している処理は, 止める. 里親募集のタブを
     * 表示していれば, すぐに, そうでなければ, 次に開いたときに, サイトから, 全てのページを取得する).
     */
    fun forget() {
        job?.cancel()
        loaded = false
        replace(emptyList())
        count.text = ""
        message.showOrHide("")
        // 里親募集のタブを表示していれば, すぐに取得し直す
        if (view.isVisible) {
            loadOnce()
        }
    }

    /**
     * 一覧を, 最初のページから最後のページまで, 1 ページずつ順に取得して, 作る (取得し終えたら,
     * 保存する. 途中で失敗したら, 保存した一覧は, 前のまま).
     */
    private suspend fun loadAll() {
        busy.isIndeterminate = true
        busy.isVisible = true
        replace(emptyList())
        AppLog.i(TAG, "Fetching all the pages of the adoption list")
        var url: String? = ADOPTION_URL
        while (url != null) {
            val page = fetchPage(url)
            append(page)
            url = page.nextUrl
        }
        if (cats.isEmpty()) {
            message.showOrHide(context.getString(R.string.adoption_empty))
        }
        AppLog.i(TAG, "Fetched ${cats.size} cats")
        save()
    }

    /**
     * サイトの最初のページから, 新しい猫を探して, 一覧の先頭に足す (新しい猫のいないページで止める.
     * 足したら, 保存して, 何匹足したかを表示する).
     *
     * 起動後の最初の表示では, 足す前に, ダイアログで聞く (「あとで」なら, 足さない. 次の更新で, また見つかる).
     * 更新 (ボタンと, 引き下げ) では, 聞かずに足して, いなければ, そう表示する.
     *
     * @param manual 更新の操作からか (false なら, 起動後の最初の表示)
     */
    private suspend fun addNew(manual: Boolean) {
        val added = mutableListOf<AdoptionCat>()
        var url: String? = ADOPTION_URL
        while (url != null) {
            val page = fetchPage(url)
            val fresh = newAdoptionCats(cats + added, page.cats)
            added += fresh
            url = page.nextUrl.takeIf { fresh.isNotEmpty() }
        }
        AppLog.i(TAG, "Found ${added.size} new cats")
        if (added.isEmpty()) {
            if (manual) {
                message.showOrHide(context.getString(R.string.adoption_no_new))
            }
            return
        }
        val accepted =
            manual ||
                suspendCancellableCoroutine { continuation ->
                    val dialog =
                        MaterialAlertDialogBuilder(context)
                            .setMessage(
                                context.resources.getQuantityString(
                                    R.plurals.adoption_new_found,
                                    added.size,
                                    added.size,
                                ),
                            ).setPositiveButton(R.string.adoption_add) { _, _ ->
                                continuation.resume(true)
                            }.setNegativeButton(R.string.adoption_later) { _, _ ->
                                continuation.resume(false)
                            }.setOnCancelListener { continuation.resume(false) }
                            .show()
                    continuation.invokeOnCancellation { dialog.dismiss() }
                }
        if (accepted) {
            // 聞いている間に, 一覧が変わっていてもよいように, もう一度, 比べる
            val fresh = newAdoptionCats(cats, added)
            replace(fresh + cats)
            save()
            message.showOrHide(
                context.resources.getQuantityString(
                    R.plurals.adoption_added,
                    fresh.size,
                    fresh.size,
                ),
            )
        }
    }

    /** 今の一覧を, 保存する. */
    private suspend fun save() {
        val snapshot = cats.toList()
        withContext(Dispatchers.IO) { store.write(snapshot) }
    }

    /**
     * 一覧を, 置き換える (保存した一覧を表示するとき, 作り直すとき).
     *
     * @param list 猫
     */
    private fun replace(list: List<AdoptionCat>) {
        cats.clear()
        cats += list
        total = null
        adapter.notifyDataSetChanged()
        showCount(done = false)
    }

    /**
     * 1 ページの猫を, 一覧の後ろに足す (ページの間で, 同じ猫が重なったら, 足さない).
     *
     * @param page ページ
     */
    private fun append(page: AdoptionPage) {
        cats += newAdoptionCats(cats, page.cats)
        total = total ?: page.total
        adapter.notifyDataSetChanged()
        showCount(done = false)
    }

    /**
     * 匹数と, 進み具合を表示する (取得している間は「読み込んだ数 / 全ての数」と, その割合のバー).
     *
     * @param done 全てのページを取得し終えたか (失敗して, 止まったときも)
     */
    private fun showCount(done: Boolean) {
        val all = total
        val size = cats.size
        count.text =
            if (done || all == null) {
                context.resources.getQuantityString(R.plurals.adoption_count, size, size)
            } else {
                context.resources.getQuantityString(R.plurals.adoption_progress, all, size, all)
            }
        if (!done && all != null && all > 0) {
            busy.isIndeterminate = false
            busy.max = all
            busy.setProgressCompat(size.coerceAtMost(all), true)
        }
    }

    /**
     * 見出し (題名, 匹数, 更新のボタン) を作る.
     *
     * @return 見出し
     */
    private fun header(): LinearLayout =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                context
                    .styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.adoption_title) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(count)
            addView(refreshButton)
        }
}
