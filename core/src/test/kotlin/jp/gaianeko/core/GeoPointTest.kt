/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [GeoPoint] の単体テスト. */
class GeoPointTest {
    /** 範囲の端 (南極, 北極, 日付変更線) は, 作れる. */
    @Test
    fun acceptsTheLimits() {
        assertEquals(-90.0, GeoPoint(-90.0, -180.0).latitude)
        assertEquals(180.0, GeoPoint(90.0, 180.0).longitude)
    }

    /** 緯度が範囲の外 (NaN を含む) なら, 作れない. */
    @Test
    fun rejectsLatitudeOutOfRange() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(90.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(Double.NaN, 0.0) }
    }

    /** 経度が範囲の外なら, 作れない. */
    @Test
    fun rejectsLongitudeOutOfRange() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, -180.1) }
    }
}
