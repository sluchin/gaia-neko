/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [boundsOf] と [formatTakenAt] の単体テスト. */
class GeoBoundsTest {
    /** 全ての位置を含む, 最小の長方形になる. */
    @Test
    fun boundsCoverAllPoints() {
        val bounds =
            boundsOf(listOf(GeoPoint(10.0, 20.0), GeoPoint(-5.0, 30.0), GeoPoint(7.0, -1.0)))

        assertEquals(GeoBounds(-5.0, 10.0, -1.0, 30.0), bounds)
    }

    /** 位置が 1 つなら, 幅のない長方形になる. */
    @Test
    fun boundsOfOnePoint() {
        assertEquals(GeoBounds(1.0, 1.0, 2.0, 2.0), boundsOf(listOf(GeoPoint(1.0, 2.0))))
    }

    /** 位置がなければ, null. */
    @Test
    fun boundsOfNothing() {
        assertNull(boundsOf(emptyList()))
    }

    /** 撮影した時刻は, 指定したタイムゾーンで, 表示する. */
    @Test
    fun takenAtUsesTheZone() {
        // 2026-10-11 06:30:00 UTC = 15:30:00 JST
        assertEquals("2026-10-11 15:30:00", formatTakenAt(1791700200, ZoneId.of("Asia/Tokyo")))
    }

    /** データクラスの, 文字列, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val bounds = GeoBounds(1.0, 2.0, 3.0, 4.0)

        assertEquals(bounds, bounds.copy())
        assertEquals(bounds.hashCode(), bounds.copy().hashCode())
        assertEquals(
            "GeoBounds(minLatitude=1.0, maxLatitude=2.0, minLongitude=3.0, maxLongitude=4.0)",
            bounds.toString(),
        )
        assertEquals(
            listOf(1.0, 2.0, 3.0, 4.0),
            listOf(
                bounds.component1(),
                bounds.component2(),
                bounds.component3(),
                bounds.component4(),
            ),
        )
    }

    /** 位置は, 小数点以下 6 桁で, 緯度, 経度の順に表示する. */
    @Test
    fun pointIsFormatted() {
        assertEquals("35.123457, -139.500000", formatPoint(GeoPoint(35.1234567, -139.5)))
    }
}
