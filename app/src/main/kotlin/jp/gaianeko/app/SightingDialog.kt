/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import jp.gaianeko.core.PhotoSource
import jp.gaianeko.core.Sighting
import jp.gaianeko.core.SightingRemover
import jp.gaianeko.core.formatPoint
import jp.gaianeko.core.formatTakenAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** 写真の, 表示する大きさの目安 (px). */
private const val PHOTO_MAX_PX = 1024

/** 何枚目かの文字の大きさ (sp). */
private const val COUNTER_TEXT_SP = 20f

/** 余白 (dp). */
private const val GAP_DP = 8

/**
 * 同じ場所の写真を, 1 枚ずつ表示するダイアログ (左右にスワイプして, 次々に見る. 写真を長押しすると, 削除する).
 *
 * @property context コンテキスト (画面)
 * @property scope コルーチンのスコープ (画面のスレッドで動く)
 * @property photos 写真の読み出し
 * @property remover 目撃と写真の削除
 * @property sightings 同じ場所の目撃 (新しい順. 削除すると, 取り除く)
 * @property onRemoved 目撃を削除したとき (全て削除して, ダイアログを閉じるときも, 呼ぶ)
 */
class SightingDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val photos: PhotoSource,
    private val remover: SightingRemover,
    private val sightings: MutableList<Sighting>,
    private val onRemoved: (Sighting) -> Unit,
) {
    private var index = 0
    private val details = TextView(context)
    private val counter =
        TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = COUNTER_TEXT_SP
            setTypeface(typeface, Typeface.BOLD)
        }
    private val hint = TextView(context).apply { setText(R.string.photo_hint) }
    private val image =
        SwipeImageView(context, { move(it) }, { confirmRemove() })
    private val dialog: AlertDialog =
        AlertDialog
            .Builder(context)
            .setView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    details.setPadding(
                        context.dp(GAP_DP * 2),
                        context.dp(GAP_DP),
                        context.dp(
                            GAP_DP * 2,
                        ),
                        context.dp(GAP_DP),
                    )
                    addView(counter)
                    addView(details)
                    addView(hint)
                    addView(image)
                },
            ).setPositiveButton(R.string.close, null)
            .create()

    /** ダイアログを表示する (最初は, 一番新しい写真). */
    fun show() {
        dialog.show()
        showCurrent()
    }

    /**
     * 前後の写真に移る (端では, 動かない).
     *
     * @param delta 次なら 1, 前なら -1
     */
    private fun move(delta: Int) {
        val next = (index + delta).coerceIn(0, sightings.size - 1)
        if (next != index) {
            index = next
            showCurrent()
        }
    }

    /** 今の目撃の, タイトル (撮影日時), 何枚目か (1 / 3 の形), 位置, 撮影者を表示して, 写真を取得して表示する. */
    private fun showCurrent() {
        val sighting = sightings[index]
        dialog.setTitle(formatTakenAt(sighting.takenAt, ZoneId.systemDefault()))
        counter.text = context.getString(R.string.photo_counter, index + 1, sightings.size)
        details.text =
            context.getString(
                R.string.sighting_details,
                formatPoint(sighting.point),
                sighting.photographer ?: context.getString(R.string.unknown),
                sighting.userId,
            )
        image.setImageBitmap(null)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { photos.get(sighting.photoKey) } }
                .onSuccess { jpeg ->
                    if (sightings.getOrNull(index) ==
                        sighting
                    ) {
                        image.setImageBitmap(decodePhoto(jpeg, PHOTO_MAX_PX))
                    }
                }.onFailure { details.text = context.getString(R.string.error, it.message) }
        }
    }

    /** 削除してよいかを, 確認する (長押しのとき). */
    private fun confirmRemove() {
        AlertDialog
            .Builder(context)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> remove() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 今の写真と目撃を削除する (成功したら, 次の写真を表示する. 全て削除したら, ダイアログを閉じる). */
    private fun remove() {
        val sighting = sightings[index]
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { remover.remove(sighting) } }
                .onSuccess {
                    sightings.remove(sighting)
                    onRemoved(sighting)
                    if (sightings.isEmpty()) {
                        dialog.dismiss()
                    } else {
                        index = index.coerceAtMost(sightings.size - 1)
                        showCurrent()
                    }
                }.onFailure { details.text = context.getString(R.string.error, it.message) }
        }
    }
}
