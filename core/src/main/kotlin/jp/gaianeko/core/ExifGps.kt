/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

/** 度分秒の項目の数 (度, 分, 秒). */
private const val DMS_PARTS = 3

/** 1 度の分数. */
private const val MINUTES_PER_DEGREE = 60.0

/** 1 度の秒数. */
private const val SECONDS_PER_DEGREE = 3600.0

/**
 * Exif の GPS の座標 (度, 分, 秒) を, 度の小数にする.
 *
 * Exif の座標は, 「35/1,39/1,2975/100」のように, 分数を, コンマで 3 つ並べた文字列 (度, 分, 秒).
 * 向きは, 北緯 (N) と東経 (E) が正, 南緯 (S) と西経 (W) が負.
 *
 * @param value 座標の文字列 (度, 分, 秒の分数をコンマで区切る)
 * @param ref 向き (N, S, E, W のどれか)
 * @return 度の小数. どれかがない, または, 読めないとき (分母が 0 を含む) は, null
 */
fun parseExifCoordinate(
    value: String?,
    ref: String?,
): Double? {
    val sign = signOf(ref)
    val parts = value?.let { parseDms(it) }
    return if (sign != null && parts != null) {
        sign * (parts[0] + parts[1] / MINUTES_PER_DEGREE + parts[2] / SECONDS_PER_DEGREE)
    } else {
        null
    }
}

/**
 * 向きから, 符号を求める.
 *
 * @param ref 向き (N, S, E, W のどれか)
 * @return 北と東は 1, 南と西は -1. それ以外 (ないときを含む) は, null
 */
private fun signOf(ref: String?): Double? =
    when (ref) {
        "N", "E" -> 1.0
        "S", "W" -> -1.0
        else -> null
    }

/**
 * 「度,分,秒」(それぞれ, 分数) の文字列を, 3 つの小数にする.
 *
 * @param value 座標の文字列
 * @return 度, 分, 秒の 3 つの小数. 項目が 3 つでない, または, どれかが読めないときは, null
 */
private fun parseDms(value: String): List<Double>? {
    val items = value.split(",")
    val parts = items.mapNotNull { parseFraction(it) }
    return if (items.size == DMS_PARTS && parts.size == DMS_PARTS) parts else null
}

/**
 * 「分子/分母」の文字列を, 小数にする.
 *
 * @param text 分数の文字列 (例: 2975/100)
 * @return 小数. 形が違う, 数でない, 分母が 0 のときは, null
 */
private fun parseFraction(text: String): Double? {
    val parts = text.trim().split("/")
    val numerator = parts[0].toDoubleOrNull()
    val denominator = parts.takeIf { it.size == 2 }?.get(1)?.toDoubleOrNull()
    return if (numerator != null && denominator != null && denominator != 0.0) {
        numerator / denominator
    } else {
        null
    }
}

/**
 * Exif の緯度と経度から, 位置を作る.
 *
 * @param latitude 緯度の文字列
 * @param latitudeRef 緯度の向き (N か S)
 * @param longitude 経度の文字列
 * @param longitudeRef 経度の向き (E か W)
 * @return 位置. 読めないとき, または, 範囲の外のときは, null
 */
fun parseExifGps(
    latitude: String?,
    latitudeRef: String?,
    longitude: String?,
    longitudeRef: String?,
): GeoPoint? {
    val lat = parseExifCoordinate(latitude, latitudeRef)
    val lon = parseExifCoordinate(longitude, longitudeRef)
    return if (lat != null && lon != null) geoPointOrNull(lat, lon) else null
}
