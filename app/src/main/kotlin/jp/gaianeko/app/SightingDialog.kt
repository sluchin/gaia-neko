/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

/** 余白 (dp). */
private const val GAP_DP = 8

/** ダイアログの左右の余白 (dp. Material 3 のダイアログの余白に合わせる). */
private const val SIDE_DP = 24

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
    private val details =
        context.styledText(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
    private val counter =
        context.badgeText().apply { gravity = Gravity.CENTER }
    private val hint =
        context
            .styledText(
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            ).apply { setText(R.string.photo_hint) }
    private val image =
        SwipeImageView(context, { move(it) }, { confirmRemove() })
    private val dialog: AlertDialog =
        MaterialAlertDialogBuilder(context)
            .setView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(
                        context.dp(SIDE_DP),
                        context.dp(GAP_DP),
                        context.dp(SIDE_DP),
                        0,
                    )
                    addView(counter)
                    addSpaced(image, GAP_DP * 2)
                    addSpaced(details, GAP_DP * 2)
                    addSpaced(hint, GAP_DP)
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
        MaterialAlertDialogBuilder(context)
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
