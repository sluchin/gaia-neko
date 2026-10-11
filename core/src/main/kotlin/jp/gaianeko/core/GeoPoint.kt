/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.math.abs

/** 緯度の最大値 (度). */
private const val MAX_LATITUDE = 90.0

/** 経度の最大値 (度). */
private const val MAX_LONGITUDE = 180.0

/**
 * 地図上の位置.
 *
 * @property latitude 緯度 (度. 北が正. -90 から 90)
 * @property longitude 経度 (度. 東が正. -180 から 180)
 * @throws IllegalArgumentException 緯度か経度が, 範囲の外 (NaN を含む) のとき
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(isLatitude(latitude)) { "latitude is out of range" }
        require(isLongitude(longitude)) { "longitude is out of range" }
    }
}

/**
 * 緯度として正しいかを判定する (NaN は, 正しくない).
 *
 * @param value 緯度 (度)
 * @return -90 から 90 の範囲なら true
 */
private fun isLatitude(value: Double): Boolean = abs(value) <= MAX_LATITUDE

/**
 * 経度として正しいかを判定する (NaN は, 正しくない).
 *
 * @param value 経度 (度)
 * @return -180 から 180 の範囲なら true
 */
private fun isLongitude(value: Double): Boolean = abs(value) <= MAX_LONGITUDE

/**
 * 緯度と経度が範囲の中なら, 位置を作る (範囲の外なら, 例外を投げずに, null).
 *
 * @param latitude 緯度 (度)
 * @param longitude 経度 (度)
 * @return 位置. 範囲の外のときは, null
 */
fun geoPointOrNull(
    latitude: Double,
    longitude: Double,
): GeoPoint? =
    if (isLatitude(latitude) &&
        isLongitude(longitude)
    ) {
        GeoPoint(latitude, longitude)
    } else {
        null
    }
