/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.DeleteItemRequest
import aws.sdk.kotlin.services.dynamodb.model.DeleteItemResponse
import aws.sdk.kotlin.services.dynamodb.model.PutItemRequest
import aws.sdk.kotlin.services.dynamodb.model.PutItemResponse
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import aws.smithy.kotlin.runtime.content.toByteArray
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** [S3PhotoStore] と [DynamoDbSightingStore] の単体テスト (AWS のクライアントは, 偽物). */
class SightingStoresTest {
    /** 写真は, バケット, キー, Content-Type, 中身を指定して, S3 に置く. */
    @Test
    fun s3PutsTheJpeg() =
        runTest {
            val client = mockk<S3Client>()
            val request = slot<PutObjectRequest>()
            coEvery { client.putObject(capture(request)) } returns PutObjectResponse {}

            S3PhotoStore(client, "bucket-1").put("photos/u/s.jpg", byteArrayOf(1, 2, 3))

            assertEquals("bucket-1", request.captured.bucket)
            assertEquals("photos/u/s.jpg", request.captured.key)
            assertEquals("image/jpeg", request.captured.contentType)
            assertContentEquals(byteArrayOf(1, 2, 3), request.captured.body?.toByteArray())
        }

    /** 目撃は, 全ての項目を, 上書きしない条件で, DynamoDB に書く. */
    @Test
    fun dynamoDbPutsTheSighting() =
        runTest {
            val client = mockk<DynamoDbClient>()
            val request = slot<PutItemRequest>()
            coEvery { client.putItem(capture(request)) } returns PutItemResponse {}
            val sighting =
                Sighting("s-1", "user-1", GeoPoint(1.5, -2.5), 1000, "photos/user-1/s-1.jpg")

            DynamoDbSightingStore(client, "table-1").put(sighting)

            assertEquals("table-1", request.captured.tableName)
            assertEquals(
                mapOf(
                    "userId" to AttributeValue.S("user-1"),
                    "id" to AttributeValue.S("s-1"),
                    "latitude" to AttributeValue.N("1.5"),
                    "longitude" to AttributeValue.N("-2.5"),
                    "takenAt" to AttributeValue.N("1000"),
                    "photoKey" to AttributeValue.S("photos/user-1/s-1.jpg"),
                ),
                request.captured.item,
            )
            assertEquals("attribute_not_exists(#id)", request.captured.conditionExpression)
            assertEquals(mapOf("#id" to "id"), request.captured.expressionAttributeNames)
        }

    /** 撮影者がいれば, 項目に加えて書く. */
    @Test
    fun dynamoDbPutsThePhotographer() =
        runTest {
            val client = mockk<DynamoDbClient>()
            val request = slot<PutItemRequest>()
            coEvery { client.putItem(capture(request)) } returns PutItemResponse {}
            val sighting =
                Sighting("s-1", "user-1", GeoPoint(1.5, -2.5), 1000, "k", "a@example.com")

            DynamoDbSightingStore(client, "table-1").put(sighting)

            assertEquals(
                AttributeValue.S("a@example.com"),
                request.captured.item?.get("photographer"),
            )
        }

    /** 写真を消すときは, バケットとキーを指定する. */
    @Test
    fun s3DeletesThePhoto() =
        runTest {
            val client = mockk<S3Client>()
            val request = slot<DeleteObjectRequest>()
            coEvery { client.deleteObject(capture(request)) } returns DeleteObjectResponse {}

            S3PhotoStore(client, "bucket-1").delete("photos/u/s.jpg")

            assertEquals("bucket-1", request.captured.bucket)
            assertEquals("photos/u/s.jpg", request.captured.key)
        }

    /** 目撃を消すときは, パーティションキーとソートキーを指定する. */
    @Test
    fun dynamoDbDeletesTheSighting() =
        runTest {
            val client = mockk<DynamoDbClient>()
            val request = slot<DeleteItemRequest>()
            coEvery { client.deleteItem(capture(request)) } returns DeleteItemResponse {}

            DynamoDbSightingStore(
                client,
                "table-1",
            ).delete(Sighting("s-1", "user-1", GeoPoint(1.0, 2.0), 1, "k"))

            assertEquals("table-1", request.captured.tableName)
            assertEquals(
                mapOf("userId" to AttributeValue.S("user-1"), "id" to AttributeValue.S("s-1")),
                request.captured.key,
            )
        }
}
