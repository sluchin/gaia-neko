/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.scan
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.toByteArray

/** 文字列の属性の数 (userId, id, photoKey). */
private const val TEXT_ATTRIBUTES = 3

/** 目撃を読み出す入り口 (AWS では DynamoDB. テストでは, 偽物). 管理者だけが使える. */
interface SightingSource {
    /**
     * 全員の目撃を, 撮影した時刻の新しい順に, 読み出す.
     *
     * @return 目撃 (読めない項目は, 除く)
     */
    suspend fun all(): List<Sighting>
}

/** 写真を読み出す入り口 (AWS では S3. テストでは, 偽物). 管理者だけが使える. */
interface PhotoSource {
    /**
     * 写真 (JPEG) を読み出す.
     *
     * @param key 写真の, S3 のキー
     * @return 写真の JPEG
     * @throws IllegalStateException 写真の中身が, 応答にないとき
     */
    suspend fun get(key: String): ByteArray
}

/**
 * 目撃を, DynamoDB のテーブルから, 全て読み出す ([SightingSource]).
 *
 * Scan を使う (管理者のロールの権限). 結果が 1 回の上限 (1 MB) を超えるときは, lastEvaluatedKey で
 * 続きを読み出す.
 *
 * @property client DynamoDB のクライアント
 * @property tableName テーブル名
 */
class DynamoDbSightingSource(
    private val client: DynamoDbClient,
    private val tableName: String,
) : SightingSource {
    override suspend fun all(): List<Sighting> {
        val sightings = mutableListOf<Sighting>()
        var startKey: Map<String, AttributeValue>? = null
        do {
            val response =
                client.scan {
                    tableName = this@DynamoDbSightingSource.tableName
                    exclusiveStartKey = startKey
                }
            response.items.orEmpty().mapNotNullTo(sightings) { toSighting(it) }
            startKey = response.lastEvaluatedKey
        } while (startKey != null)
        return sightings.sortedByDescending { it.takenAt }
    }
}

/**
 * DynamoDB の 1 件を, [Sighting] にする.
 *
 * どれかの項目がない, 数として読めない, 位置が範囲の外のときは, null (その項目は, 除く).
 *
 * @param item DynamoDB の 1 件
 * @return 目撃. 読めないときは, null
 */
internal fun toSighting(item: Map<String, AttributeValue>): Sighting? {
    val texts =
        listOf(
            "userId",
            "id",
            "photoKey",
        ).mapNotNull { (item[it] as? AttributeValue.S)?.value }
    val takenAt = number(item["takenAt"])?.toLongOrNull()
    val latitude = number(item["latitude"])?.toDoubleOrNull()
    val longitude = number(item["longitude"])?.toDoubleOrNull()
    val point =
        if (latitude != null &&
            longitude != null
        ) {
            geoPointOrNull(latitude, longitude)
        } else {
            null
        }
    return if (texts.size == TEXT_ATTRIBUTES && takenAt != null && point != null) {
        Sighting(texts[1], texts[0], point, takenAt, texts[2])
    } else {
        null
    }
}

/**
 * 数値 (N) の属性の, 文字列を取り出す.
 *
 * @param value 属性の値
 * @return 数の文字列. 数値の属性でないときは, null
 */
private fun number(value: AttributeValue?): String? = (value as? AttributeValue.N)?.value

/**
 * 写真を, S3 のバケットから読み出す ([PhotoSource]).
 *
 * @property client S3 のクライアント
 * @property bucket バケット名
 */
class S3PhotoSource(
    private val client: S3Client,
    private val bucket: String,
) : PhotoSource {
    override suspend fun get(key: String): ByteArray =
        client.getObject(
            GetObjectRequest {
                bucket = this@S3PhotoSource.bucket
                this.key = key
            },
        ) { response ->
            checkNotNull(response.body) { "S3 did not return the photo" }.toByteArray()
        }
}
