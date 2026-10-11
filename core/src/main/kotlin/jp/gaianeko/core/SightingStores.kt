/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.putItem
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream

/** 写真の, Content-Type. */
private const val JPEG_CONTENT_TYPE = "image/jpeg"

/** 属性名: 利用者 (パーティションキー). */
private const val ATTR_USER_ID = "userId"

/** 属性名: 目撃の識別子 (ソートキー). */
private const val ATTR_ID = "id"

/** 属性名: 緯度. */
private const val ATTR_LATITUDE = "latitude"

/** 属性名: 経度. */
private const val ATTR_LONGITUDE = "longitude"

/** 属性名: 撮影した時刻. */
private const val ATTR_TAKEN_AT = "takenAt"

/** 属性名: 写真の, S3 のキー. */
private const val ATTR_PHOTO_KEY = "photoKey"

/** 同じ目撃を, 上書きしないための条件 (その項目が, まだ, ないときだけ書く). */
private const val NOT_EXISTS = "attribute_not_exists(#id)"

/** 写真を保存する入り口 (AWS では S3. テストでは, 偽物). */
interface PhotoStore {
    /**
     * 写真 (JPEG) を保存する.
     *
     * @param key 保存先のキー
     * @param jpeg 写真の JPEG
     */
    suspend fun put(
        key: String,
        jpeg: ByteArray,
    )
}

/** 目撃を保存する入り口 (AWS では DynamoDB. テストでは, 偽物). */
interface SightingStore {
    /**
     * 目撃を保存する (すでに, 同じ目撃があれば, 失敗する).
     *
     * @param sighting 目撃
     */
    suspend fun put(sighting: Sighting)
}

/**
 * 写真を, S3 のバケットに保存する ([PhotoStore]).
 *
 * @property client S3 のクライアント
 * @property bucket バケット名
 */
class S3PhotoStore(
    private val client: S3Client,
    private val bucket: String,
) : PhotoStore {
    override suspend fun put(
        key: String,
        jpeg: ByteArray,
    ) {
        client.putObject {
            bucket = this@S3PhotoStore.bucket
            this.key = key
            body = ByteStream.fromBytes(jpeg)
            contentType = JPEG_CONTENT_TYPE
        }
    }
}

/**
 * 目撃を, DynamoDB のテーブルに保存する ([SightingStore]).
 *
 * @property client DynamoDB のクライアント
 * @property tableName テーブル名
 */
class DynamoDbSightingStore(
    private val client: DynamoDbClient,
    private val tableName: String,
) : SightingStore {
    override suspend fun put(sighting: Sighting) {
        client.putItem {
            tableName = this@DynamoDbSightingStore.tableName
            item =
                mapOf(
                    ATTR_USER_ID to AttributeValue.S(sighting.userId),
                    ATTR_ID to AttributeValue.S(sighting.id),
                    ATTR_LATITUDE to AttributeValue.N(sighting.point.latitude.toString()),
                    ATTR_LONGITUDE to AttributeValue.N(sighting.point.longitude.toString()),
                    ATTR_TAKEN_AT to AttributeValue.N(sighting.takenAt.toString()),
                    ATTR_PHOTO_KEY to AttributeValue.S(sighting.photoKey),
                )
            conditionExpression = NOT_EXISTS
            expressionAttributeNames = mapOf("#id" to ATTR_ID)
        }
    }
}
