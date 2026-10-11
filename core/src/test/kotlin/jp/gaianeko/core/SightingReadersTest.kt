/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.core

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.AttributeValue
import aws.sdk.kotlin.services.dynamodb.model.ScanRequest
import aws.sdk.kotlin.services.dynamodb.model.ScanResponse
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectResponse
import aws.smithy.kotlin.runtime.content.ByteStream
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 試験用の, DynamoDB の 1 件を作る (null の項目は, 入れない).
 *
 * @param id 目撃の識別子
 * @param takenAt 撮影した時刻
 * @param latitude 緯度の文字列
 * @param userId 利用者
 * @return 1 件
 */
private fun item(
    id: String? = "s-1",
    takenAt: String? = "1000",
    latitude: String? = "35.5",
    userId: String? = "user-1",
): Map<String, AttributeValue> =
    listOfNotNull(
        id?.let { "id" to AttributeValue.S(it) },
        userId?.let { "userId" to AttributeValue.S(it) },
        takenAt?.let { "takenAt" to AttributeValue.N(it) },
        latitude?.let { "latitude" to AttributeValue.N(it) },
        "longitude" to AttributeValue.N("139.5"),
        "photoKey" to AttributeValue.S("photos/user-1/s-1.jpg"),
    ).toMap()

/** [DynamoDbSightingSource] と [S3PhotoSource] の単体テスト (AWS のクライアントは, 偽物). */
class SightingReadersTest {
    private val dynamoDb = mockk<DynamoDbClient>()

    /** 項目が全てそろっていれば, 目撃になる. */
    @Test
    fun toSightingReadsAnItem() {
        assertEquals(
            Sighting("s-1", "user-1", GeoPoint(35.5, 139.5), 1000, "photos/user-1/s-1.jpg"),
            toSighting(item()),
        )
    }

    /** 項目がない, 数でない, 位置が範囲の外のものは, 読めない (null). */
    @Test
    fun toSightingRejectsBrokenItems() {
        assertNull(toSighting(item(id = null)))
        assertNull(toSighting(item(userId = null)))
        assertNull(toSighting(item(takenAt = null)))
        assertNull(toSighting(item(takenAt = "x")))
        assertNull(toSighting(item(latitude = null)))
        assertNull(toSighting(item(latitude = "y")))
        assertNull(toSighting(item(latitude = "91")))
        assertNull(toSighting(item() - "photoKey"))
        assertNull(toSighting(item() - "longitude"))
    }

    /** 続きがあれば, 続きを読み出して, 新しい順にまとめる. 読めない項目は, 除く. */
    @Test
    fun allFollowsThePagesAndSortsNewestFirst() =
        runTest {
            val requests = mutableListOf<Map<String, AttributeValue>?>()
            val request = slot<ScanRequest>()
            coEvery { dynamoDb.scan(capture(request)) } answers {
                requests += request.captured.exclusiveStartKey
                if (request.captured.exclusiveStartKey == null) {
                    ScanResponse {
                        items = listOf(item(id = "old", takenAt = "100"), item(id = null))
                        lastEvaluatedKey = mapOf("id" to AttributeValue.S("old"))
                    }
                } else {
                    ScanResponse { items = listOf(item(id = "new", takenAt = "200")) }
                }
            }

            val all = DynamoDbSightingSource(dynamoDb, "table-1").all()

            assertEquals(listOf("new", "old"), all.map { it.id })
            assertEquals(2, requests.size)
            assertNull(requests[0])
            assertEquals(mapOf("id" to AttributeValue.S("old")), requests[1])
            assertEquals("table-1", request.captured.tableName)
        }

    /** 項目が, 応答にないときも, 空の一覧になる. */
    @Test
    fun allWithoutItems() =
        runTest {
            coEvery { dynamoDb.scan(any<ScanRequest>()) } returns ScanResponse {}

            assertEquals(emptyList(), DynamoDbSightingSource(dynamoDb, "table-1").all())
        }

    /** 写真は, バケットとキーを指定して, S3 から読む. */
    @Test
    fun photoIsReadFromS3() =
        runTest {
            val s3 = mockk<S3Client>()
            val request = slot<GetObjectRequest>()
            coEvery {
                s3.getObject(capture(request), any<suspend (GetObjectResponse) -> ByteArray>())
            } coAnswers {
                secondArg<suspend (GetObjectResponse) -> ByteArray>()
                    .invoke(GetObjectResponse { body = ByteStream.fromBytes(byteArrayOf(1, 2)) })
            }

            val jpeg = S3PhotoSource(s3, "bucket-1").get("photos/u/s.jpg")

            assertContentEquals(byteArrayOf(1, 2), jpeg)
            assertEquals("bucket-1", request.captured.bucket)
            assertEquals("photos/u/s.jpg", request.captured.key)
        }

    /** 写真の中身が, 応答にないときは, 例外にする. */
    @Test
    fun photoWithoutBodyFails() =
        runTest {
            val s3 = mockk<S3Client>()
            coEvery {
                s3.getObject(
                    any<GetObjectRequest>(),
                    any<
                        suspend (
                            GetObjectResponse,
                        ) -> ByteArray,
                    >(),
                )
            } coAnswers {
                secondArg<suspend (GetObjectResponse) -> ByteArray>().invoke(GetObjectResponse {})
            }

            val error =
                assertFailsWith<IllegalStateException> { S3PhotoSource(s3, "bucket-1").get("k") }

            assertEquals("S3 did not return the photo", error.message)
        }
}
