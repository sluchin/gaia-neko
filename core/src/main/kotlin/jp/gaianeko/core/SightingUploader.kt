/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import java.util.UUID

/**
 * 撮影した猫の目撃を, 保存する (写真を S3 に, 位置と時刻と写真のキーを DynamoDB に).
 *
 * 写真を先に保存して, 成功したら, 目撃を保存する (写真のない目撃が, 残らないように). 目撃の保存に
 * 失敗したときは, 写真が残る (S3 のライフサイクルで, あとで, 消せる).
 *
 * @property photos 写真の保存先
 * @property sightings 目撃の保存先
 * @property userId 利用者の識別子を返す関数 (ID プールの ID)
 * @property photographer 撮影した利用者のメールアドレスを返す関数 (表示用. ないときは, null)
 * @property newId 目撃の識別子を作る関数 (テストで, 差し替える)
 */
class SightingUploader(
    private val photos: PhotoStore,
    private val sightings: SightingStore,
    private val userId: suspend () -> String,
    private val photographer: suspend () -> String? = { null },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * 目撃を保存する.
     *
     * @param jpeg 写真の JPEG
     * @param point 撮影した位置
     * @param takenAt 撮影した時刻 (UNIX 時刻 (s))
     * @return 保存した目撃
     */
    suspend fun upload(
        jpeg: ByteArray,
        point: GeoPoint,
        takenAt: Long,
    ): Sighting {
        val user = userId()
        val id = newId()
        val sighting = Sighting(id, user, point, takenAt, photoKey(user, id), photographer())
        photos.put(sighting.photoKey, jpeg)
        sightings.put(sighting)
        return sighting
    }
}
