/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.math.roundToLong

/** 同じ場所とみなす, 位置の丸めの倍率 (小数点以下 4 桁. 約 11 m). */
private const val PLACE_SCALE = 10_000.0

/**
 * 同じ場所で撮影された, 目撃のまとまり (地図の 1 つのピンになる. 写真を, 次々に見られる).
 *
 * @property point ピンを置く位置 (一番新しい目撃の位置)
 * @property sightings 目撃 (撮影した時刻の新しい順. 1 件以上)
 */
data class SightingGroup(
    val point: GeoPoint,
    val sightings: List<Sighting>,
)

/**
 * 目撃を, 同じ場所ごとにまとめる (緯度と経度を, 小数点以下 4 桁 (約 11 m) に丸めて, 同じなら, 同じ場所).
 *
 * 丸めの境目をまたぐ, ごく近い位置は, 別の場所になることがある.
 *
 * @param all 目撃 (順序は, 問わない)
 * @return 場所ごとのまとまり (まとまりの中は, 撮影した時刻の新しい順. まとまりの並びは, 一番新しい目撃の順)
 */
fun groupByPlace(all: List<Sighting>): List<SightingGroup> =
    all
        .sortedByDescending { it.takenAt }
        .groupBy { place(it.point) }
        .values
        .map { SightingGroup(it.first().point, it) }

/**
 * 位置を, 同じ場所とみなす, 丸めた値にする.
 *
 * @param point 位置
 * @return 丸めた緯度と経度の組
 */
private fun place(point: GeoPoint): Pair<Long, Long> =
    Pair(
        (point.latitude * PLACE_SCALE).roundToLong(),
        (point.longitude * PLACE_SCALE).roundToLong(),
    )
