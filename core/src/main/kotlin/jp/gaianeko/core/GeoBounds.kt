/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 撮影した時刻の表示の形式 (例: 2026-10-11 15:30:00). */
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * 地図の表示範囲 (全ての位置を含む, 最小の長方形).
 *
 * @property minLatitude 緯度の最小 (度)
 * @property maxLatitude 緯度の最大 (度)
 * @property minLongitude 経度の最小 (度)
 * @property maxLongitude 経度の最大 (度)
 */
data class GeoBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

/**
 * 位置の並びから, 全てを含む表示範囲を求める (地図を, 全ての猫が見える位置に合わせるため).
 *
 * @param points 位置
 * @return 表示範囲. 位置がないときは, null
 */
fun boundsOf(points: List<GeoPoint>): GeoBounds? =
    if (points.isEmpty()) {
        null
    } else {
        GeoBounds(
            points.map { it.latitude }.min(),
            points.map { it.latitude }.max(),
            points.map { it.longitude }.min(),
            points.map { it.longitude }.max(),
        )
    }

/**
 * 位置を, 表示用の文字列にする (小数点以下 6 桁. 例: 35.123456, 139.123456).
 *
 * @param point 位置
 * @return 「緯度, 経度」の文字列
 */
fun formatPoint(point: GeoPoint): String =
    "%.6f, %.6f".format(Locale.ROOT, point.latitude, point.longitude)

/**
 * 撮影した時刻を, 表示用の文字列にする (例: 2026-10-11 15:30:00).
 *
 * @param takenAt 撮影した時刻 (UNIX 時刻 (s))
 * @param zone 時刻を表示するタイムゾーン
 * @return 表示用の文字列
 */
fun formatTakenAt(
    takenAt: Long,
    zone: ZoneId,
): String = TIME_FORMAT.format(Instant.ofEpochSecond(takenAt).atZone(zone))
