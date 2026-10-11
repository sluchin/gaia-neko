/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 試験用の保存先 (呼ばれた順を, 記録する). 失敗を, 設定できる. */
private class Recorder(
    private val failPhoto: Boolean = false,
    private val failSighting: Boolean = false,
) : PhotoStore,
    SightingStore {
    /** 呼ばれた順の記録. */
    val calls = mutableListOf<String>()

    /** 保存された写真. */
    var jpeg: ByteArray? = null

    /** 保存された目撃. */
    var sighting: Sighting? = null

    override suspend fun put(
        key: String,
        jpeg: ByteArray,
    ) {
        calls += "photo"
        if (failPhoto) throw IOException("photo")
        this.jpeg = jpeg
    }

    override suspend fun put(sighting: Sighting) {
        calls += "sighting"
        if (failSighting) throw IOException("sighting")
        this.sighting = sighting
    }
}

/** [SightingUploader] の単体テスト. */
class SightingUploaderTest {
    private val point = GeoPoint(35.0, 139.0)

    private fun uploader(recorder: Recorder) =
        SightingUploader(recorder, recorder, { "user-1" }, newId = { "s-1" })

    /** 写真を先に保存して, 次に, 目撃を保存する. 目撃には, 利用者, 位置, 時刻, 写真のキーが入る. */
    @Test
    fun uploadSavesThePhotoFirst() =
        runTest {
            val recorder = Recorder()

            val sighting = uploader(recorder).upload(byteArrayOf(9), point, 1000)

            assertEquals(listOf("photo", "sighting"), recorder.calls)
            assertEquals(Sighting("s-1", "user-1", point, 1000, "photos/user-1/s-1.jpg"), sighting)
            assertEquals(sighting, recorder.sighting)
            assertContentEquals(byteArrayOf(9), recorder.jpeg)
        }

    /** 写真の保存に失敗したら, 目撃は保存しない. */
    @Test
    fun uploadStopsWhenThePhotoFails() =
        runTest {
            val recorder = Recorder(failPhoto = true)

            assertFailsWith<IOException> { uploader(recorder).upload(byteArrayOf(9), point, 1000) }

            assertEquals(listOf("photo"), recorder.calls)
        }

    /** 目撃の保存に失敗したら, 例外を伝える. */
    @Test
    fun uploadFailsWhenTheSightingFails() =
        runTest {
            val recorder = Recorder(failSighting = true)

            assertFailsWith<IOException> { uploader(recorder).upload(byteArrayOf(9), point, 1000) }
        }

    /** 識別子を渡さないときは, UUID を作る (呼ぶたびに, 違う). */
    @Test
    fun uploadCreatesUniqueIds() =
        runTest {
            val recorder = Recorder()
            val real = SightingUploader(recorder, recorder, { "user-1" })

            val a = real.upload(byteArrayOf(1), point, 1)
            val b = real.upload(byteArrayOf(1), point, 1)

            assertTrue(a.id != b.id)
        }

    /** 撮影者を返す関数を渡せば, 目撃に入る. */
    @Test
    fun uploadKeepsThePhotographer() =
        runTest {
            val recorder = Recorder()
            val uploader =
                SightingUploader(recorder, recorder, { "user-1" }, { "a@example.com" }, { "s-1" })

            assertEquals("a@example.com", uploader.upload(byteArrayOf(1), point, 1).photographer)
        }
}
