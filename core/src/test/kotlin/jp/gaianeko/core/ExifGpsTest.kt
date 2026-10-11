/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 浮動小数点の比較の許容差. */
private const val DELTA = 1e-9

/** 試験の緯度 (度分秒の分数. 十分に小さい値の, ダミー). */
private const val LAT = "10/1,30/1,3600/100"

/** 試験の経度 (度分秒の分数). */
private const val LON = "20/1,15/1,0/1"

/** [parseExifCoordinate] と [parseExifGps] の単体テスト. */
class ExifGpsTest {
    /** 度分秒を, 度の小数にする (10 度 30 分 36 秒 = 10.51 度). */
    @Test
    fun coordinateConvertsDegreesMinutesSeconds() {
        assertEquals(10.51, assertNotNull(parseExifCoordinate(LAT, "N")), DELTA)
    }

    /** 南緯と西経は, 負になる. */
    @Test
    fun coordinateSignFollowsTheRef() {
        assertEquals(-10.51, assertNotNull(parseExifCoordinate(LAT, "S")), DELTA)
        assertEquals(20.25, assertNotNull(parseExifCoordinate(LON, "E")), DELTA)
        assertEquals(-20.25, assertNotNull(parseExifCoordinate(LON, "W")), DELTA)
    }

    /** 値か向きがないときは, null. */
    @Test
    fun coordinateWithoutValueOrRef() {
        assertNull(parseExifCoordinate(null, "N"))
        assertNull(parseExifCoordinate(LAT, null))
    }

    /** 向きが N, S, E, W 以外のときは, null. */
    @Test
    fun coordinateWithUnknownRef() {
        assertNull(parseExifCoordinate(LAT, "X"))
    }

    /** 項目が 3 つでない, 余計なコンマがある, 分数でない, 分母が 0 のときは, null. */
    @Test
    fun coordinateWithBrokenValue() {
        assertNull(parseExifCoordinate("10/1,30/1", "N"))
        assertNull(parseExifCoordinate("10/1,30/1,0/1,", "N"))
        assertNull(parseExifCoordinate("10,30/1,0/1", "N"))
        assertNull(parseExifCoordinate("10/1/2,30/1,0/1", "N"))
        assertNull(parseExifCoordinate("a/1,30/1,0/1", "N"))
        assertNull(parseExifCoordinate("10/b,30/1,0/1", "N"))
        assertNull(parseExifCoordinate("10/0,30/1,0/1", "N"))
    }

    /** 緯度と経度が読めれば, 位置になる. */
    @Test
    fun gpsBuildsThePoint() {
        val point = assertNotNull(parseExifGps(LAT, "N", LON, "E"))

        assertEquals(10.51, point.latitude, DELTA)
        assertEquals(20.25, point.longitude, DELTA)
    }

    /** 緯度か経度が読めないとき, 範囲の外のときは, null. */
    @Test
    fun gpsWithoutAValidPoint() {
        assertNull(parseExifGps(null, "N", LON, "E"))
        assertNull(parseExifGps(LAT, "N", null, "E"))
        assertNull(parseExifGps("100/1,0/1,0/1", "N", LON, "E"))
        assertNull(parseExifGps(LAT, "N", "200/1,0/1,0/1", "E"))
    }
}
