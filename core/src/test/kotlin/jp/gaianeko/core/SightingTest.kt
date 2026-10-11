/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [Sighting] と [photoKey] の単体テスト. */
class SightingTest {
    /** 写真のキーは, 利用者ごとの接頭辞の下になる. */
    @Test
    fun photoKeyIsUnderTheUserPrefix() {
        assertEquals("photos/user-1/s-1.jpg", photoKey("user-1", "s-1"))
    }

    /** 目撃は, 値で比較できる. */
    @Test
    fun sightingsAreComparedByValue() {
        val a = Sighting("s-1", "user-1", GeoPoint(1.0, 2.0), 1000, photoKey("user-1", "s-1"))

        assertEquals(a, a.copy())
    }

    /** データクラスの, 文字列, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val sighting = Sighting("s-1", "user-1", GeoPoint(1.0, 2.0), 1000, "photos/user-1/s-1.jpg")
        assertEquals(sighting.hashCode(), sighting.copy().hashCode())
        assertTrue(sighting.toString().contains("takenAt=1000"))
        assertEquals(listOf("s-1", "user-1"), listOf(sighting.component1(), sighting.component2()))
        assertEquals(GeoPoint(1.0, 2.0), sighting.component3())
        assertEquals(1000L, sighting.component4())
        assertEquals("photos/user-1/s-1.jpg", sighting.component5())
    }
}
