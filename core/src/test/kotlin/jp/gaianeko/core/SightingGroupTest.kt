/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 試験用の目撃を作る.
 *
 * @param id 識別子
 * @param takenAt 撮影した時刻
 * @param lat 緯度
 * @param lon 経度
 * @return 目撃
 */
private fun sighting(
    id: String,
    takenAt: Long,
    lat: Double,
    lon: Double,
) = Sighting(id, "user-1", GeoPoint(lat, lon), takenAt, "photos/user-1/$id.jpg")

/** [groupByPlace] と [SightingGroup] の単体テスト. */
class SightingGroupTest {
    /** 約 11 m 以内 (小数点以下 4 桁が同じ) は, 同じ場所にまとめる. 中は新しい順. */
    @Test
    fun nearbySightingsAreGrouped() {
        val groups =
            groupByPlace(
                listOf(
                    sighting("old", 100, 43.05330, 141.33880),
                    sighting("new", 300, 43.05332, 141.33884),
                    sighting("mid", 200, 43.05331, 141.33881),
                ),
            )

        assertEquals(1, groups.size)
        assertEquals(listOf("new", "mid", "old"), groups[0].sightings.map { it.id })
        assertEquals(GeoPoint(43.05332, 141.33884), groups[0].point)
    }

    /** 離れた場所は, 別のまとまり. まとまりは, 一番新しい目撃の順. */
    @Test
    fun distantSightingsAreSeparated() {
        val groups =
            groupByPlace(
                listOf(
                    sighting("a", 100, 43.0, 141.0),
                    sighting("b", 300, 35.0, 139.0),
                    sighting("c", 200, 43.0, 141.00001),
                ),
            )

        assertEquals(
            listOf(listOf("b"), listOf("c", "a")),
            groups.map { g -> g.sightings.map { it.id } },
        )
    }

    /** 目撃がなければ, まとまりもない. */
    @Test
    fun noSightingsNoGroups() {
        assertEquals(emptyList(), groupByPlace(emptyList()))
    }

    /** データクラスの, 比較, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val group = SightingGroup(GeoPoint(1.0, 2.0), emptyList())

        assertEquals(group, group.copy())
        assertEquals(group.hashCode(), group.copy().hashCode())
        assertEquals(
            "SightingGroup(point=GeoPoint(latitude=1.0, longitude=2.0), sightings=[])",
            group.toString(),
        )
        assertEquals(GeoPoint(1.0, 2.0), group.component1())
        assertEquals(emptyList(), group.component2())
    }
}
