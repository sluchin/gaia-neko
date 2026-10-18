/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.os.Bundle
import android.text.format.Formatter
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ログのタグ. */
private const val TAG = "Settings"

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/**
 * 設定の画面 (ドロワーから開く. 上の矢印か, 戻るで, 閉じる).
 *
 * 保存したデータ (里親募集の一覧と写真) の容量を表示して, 削除できる. 削除したら, 結果を
 * [android.app.Activity.RESULT_OK] にして, 前の画面 ([MainActivity]) に, 里親募集の一覧を, 捨てさせる.
 */
class SettingsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val size by lazy {
        styledText(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
    }
    private val message by lazy { cardText() }

    /**
     * 画面を作る.
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
                        androidx.appcompat.R.attr.colorPrimary,
                    ).apply { setText(R.string.settings_saved_title) },
                )
                addSpaced(
                    styledText(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
                    ).apply { setText(R.string.settings_saved_note) },
                    GAP_DP,
                )
                addSpaced(size, GAP_DP)
                addSpaced(
                    materialButton(
                        R.string.settings_clear,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                        R.drawable.ic_delete,
                    ) { confirmClear() },
                    GAP_DP,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                addSpaced(message, GAP_DP)
            }
        val root = ScrollView(this).apply { clipToPadding = false }
        root.applySystemBarInsets()
        setContentView(root)
        root.showContent(content)
        showSize()
    }

    /**
     * 上の矢印で, 閉じる.
     *
     * @return 処理したので, true
     */
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** 画面を閉じるときに, 処理を止める. */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 保存したデータの容量を, 表示する. */
    private fun showSize() {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { SavedData.size(this@SettingsActivity) }
            AppLog.d(TAG) { "Saved data: $bytes bytes" }
            size.text =
                getString(
                    R.string.settings_saved_size,
                    Formatter.formatShortFileSize(this@SettingsActivity, bytes),
                )
        }
    }

    /** 削除してよいかを聞いて, よければ, 削除する. */
    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.settings_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                scope.launch {
                    withContext(Dispatchers.IO) { SavedData.clear(this@SettingsActivity) }
                    AppLog.i(TAG, "Deleted the saved data")
                    setResult(RESULT_OK)
                    message.showOrHide(getString(R.string.settings_cleared))
                    showSize()
                }
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }
}
