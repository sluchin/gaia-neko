/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [SightingRemover] の単体テスト. */
class SightingRemoverTest {
    private val sighting =
        Sighting("s-1", "user-1", GeoPoint(35.0, 139.0), 1000, "photos/user-1/s-1.jpg")

    /** 目撃を先に消して, 次に, 写真を消す. */
    @Test
    fun removeDeletesTheSightingFirst() =
        runTest {
            val recorder = Recorder()

            SightingRemover(recorder, recorder).remove(sighting)

            assertEquals(
                listOf("delete sighting s-1", "delete photo photos/user-1/s-1.jpg"),
                recorder.calls,
            )
        }

    /** 目撃を消せなければ, 写真は消さない. */
    @Test
    fun removeKeepsThePhotoWhenTheSightingFails() =
        runTest {
            val recorder = Recorder(failSighting = true)

            assertFailsWith<IOException> { SightingRemover(recorder, recorder).remove(sighting) }

            assertEquals(listOf("delete sighting s-1"), recorder.calls)
        }

    /** 写真を消せなければ, 例外を伝える (目撃は, 消えている). */
    @Test
    fun removeFailsWhenThePhotoFails() =
        runTest {
            val recorder = Recorder(failPhoto = true)

            assertFailsWith<IOException> { SightingRemover(recorder, recorder).remove(sighting) }

            assertEquals(2, recorder.calls.size)
        }
}
