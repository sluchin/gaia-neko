/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

/**
 * 猫の目撃を, 消す (目撃を DynamoDB から, 写真を S3 から). 管理者のロールの権限だけが, できる.
 *
 * 目撃を先に消して, 成功したら, 写真を消す (写真のない目撃が, 地図に残らないように). 写真の削除に
 * 失敗したときは, 写真が残る (地図には出ない. あとで, 手で消せる).
 *
 * @property photos 写真の保存先
 * @property sightings 目撃の保存先
 */
class SightingRemover(
    private val photos: PhotoStore,
    private val sightings: SightingStore,
) {
    /**
     * 目撃と, その写真を消す.
     *
     * @param sighting 消す目撃
     */
    suspend fun remove(sighting: Sighting) {
        sightings.delete(sighting)
        photos.delete(sighting.photoKey)
    }
}
