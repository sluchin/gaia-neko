/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** 枠 (状態, メッセージ) の内側の余白 (dp). */
private const val CARD_PADDING_DP = 16

/** 印 (役割, 目撃数) の, 横の余白 (dp). */
private const val BADGE_PADDING_H_DP = 12

/** 印 (役割, 目撃数) の, 縦の余白 (dp). */
private const val BADGE_PADDING_V_DP = 4

/**
 * Material 3 の文字の見た目の, 文字の部品を作る.
 *
 * @receiver コンテキスト
 * @param appearance 文字の見た目 (`TextAppearance.Material3.*` のスタイル)
 * @param colorAttr 文字の色の属性 (なければ, テーマの既定の色)
 * @return 文字の部品
 */
fun Context.styledText(
    appearance: Int,
    colorAttr: Int? = null,
): TextView =
    TextView(this).apply {
        setTextAppearance(appearance)
        colorAttr?.let { setTextColor(MaterialColors.getColor(this, it)) }
    }

/**
 * 角を丸くした枠の中の, 文字の部品を作る (状態や, メッセージ. 文字がないときは, 隠す).
 *
 * @receiver コンテキスト
 * @return 文字の部品
 */
fun Context.cardText(): TextView =
    styledText(
        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
        com.google.android.material.R.attr.colorOnSecondaryContainer,
    ).apply {
        setBackgroundResource(R.drawable.card_background)
        val padding = dp(CARD_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        isVisible = false
    }

/**
 * 小さな印 (役割, 目撃数) の, 文字の部品を作る.
 *
 * @receiver コンテキスト
 * @return 文字の部品
 */
fun Context.badgeText(): TextView =
    styledText(
        com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
        com.google.android.material.R.attr.colorOnTertiaryContainer,
    ).apply {
        setBackgroundResource(R.drawable.badge_background)
        setPadding(
            dp(BADGE_PADDING_H_DP),
            dp(BADGE_PADDING_V_DP),
            dp(BADGE_PADDING_H_DP),
            dp(BADGE_PADDING_V_DP),
        )
    }

/**
 * 文字を表示する (空なら, 隠す).
 *
 * @receiver 文字の部品
 * @param text 文字
 */
fun TextView.showOrHide(text: String) {
    this.text = text
    isVisible = text.isNotEmpty()
}

/**
 * Material 3 のボタンを作る.
 *
 * @receiver コンテキスト
 * @param textId 文字列のリソース
 * @param styleAttr ボタンの種類の属性 (既定は, 塗りつぶしたボタン)
 * @param iconId 文字の前の, アイコンのリソース (なければ, null)
 * @param action 押されたときの処理
 * @return ボタン
 */
fun Context.materialButton(
    textId: Int,
    styleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
    iconId: Int? = null,
    action: () -> Unit,
): MaterialButton =
    MaterialButton(this, null, styleAttr).apply {
        setText(textId)
        iconId?.let { setIconResource(it) }
        setOnClickListener { action() }
    }

/**
 * 枠で囲んだ, 入力欄を作る (パスワードは, 見せるボタンを付ける).
 *
 * @receiver コンテキスト
 * @param hintId 案内の文字列のリソース
 * @param type 入力の種類 (InputType)
 * @return 入力欄の枠と, その中の入力欄
 */
fun Context.inputField(
    hintId: Int,
    type: Int,
): Pair<TextInputLayout, TextInputEditText> {
    val layout =
        TextInputLayout(
            this,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle,
        ).apply { setHint(hintId) }
    val edit =
        TextInputEditText(layout.context).apply {
            inputType = type
            isSingleLine = true
            // パスワードの種類にすると, 等幅の字体になるので, ほかの入力欄と同じ字体に戻す
            typeface = Typeface.DEFAULT
        }
    layout.addView(edit)
    if (type == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
        layout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    }
    return layout to edit
}

/**
 * 通信している間に出す, 横棒の進み具合 (終わりが分からないので, 動き続ける) を作る.
 *
 * @receiver コンテキスト
 * @return 進み具合 (最初は, 隠す)
 */
fun Context.busyIndicator(): LinearProgressIndicator =
    LinearProgressIndicator(this).apply {
        isIndeterminate = true
        visibility = View.INVISIBLE
    }

/**
 * 縦に並べる部品を, 上に余白を空けて, 足す.
 *
 * @receiver 縦に並べる部品
 * @param view 足す部品
 * @param topDp 上の余白 (dp)
 * @param width 幅 (px. 既定は, 親の幅)
 * @param height 高さ (px. 既定は, 中身の高さ)
 */
fun LinearLayout.addSpaced(
    view: View,
    topDp: Int,
    width: Int = LinearLayout.LayoutParams.MATCH_PARENT,
    height: Int = LinearLayout.LayoutParams.WRAP_CONTENT,
) {
    addView(
        view,
        LinearLayout.LayoutParams(width, height).apply {
            topMargin = context.dp(topDp)
        },
    )
}
