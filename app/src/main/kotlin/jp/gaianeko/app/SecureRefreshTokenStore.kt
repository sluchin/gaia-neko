/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.gaianeko.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import jp.gaianeko.core.RefreshTokenStore
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** ログのタグ. */
private const val TAG = "Token"

/** Android Keystore の名前. */
private const val KEYSTORE = "AndroidKeyStore"

/** 更新トークンを暗号化する鍵の別名. */
private const val KEY_ALIAS = "gaia-neko-refresh-token"

/** 暗号の方式 (AES, GCM). */
private const val TRANSFORMATION = "AES/GCM/NoPadding"

/** 鍵の長さ (ビット). */
private const val KEY_BITS = 256

/** GCM の認証タグの長さ (ビット). */
private const val TAG_BITS = 128

/** GCM の初期化ベクトルの長さ (バイト). */
private const val IV_BYTES = 12

/** 保存先 (SharedPreferences) の名前. */
private const val PREFS = "gaia_neko_auth"

/** 保存先の中の, 更新トークンの名前. */
private const val PREF_KEY = "refresh_token"

/**
 * 更新トークンを, Android Keystore の鍵で暗号化して, 端末に保存する ([RefreshTokenStore]).
 *
 * 鍵は, 端末の外に取り出せない. アプリのデータが, バックアップや, 別の端末にコピーされても, 復号できない
 * (復号に失敗したときは, 保存していないものとして扱う. もう一度, ログインする).
 *
 * @param context コンテキスト
 */
class SecureRefreshTokenStore(
    context: Context,
) : RefreshTokenStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 保存した更新トークンを読む.
     *
     * @return 更新トークン. 保存していない, または, 復号できないときは, null
     */
    override fun load(): String? {
        val stored = prefs.getString(PREF_KEY, null)
        return stored?.let {
            try {
                decrypt(Base64.getDecoder().decode(it))
            } catch (e: GeneralSecurityException) {
                // トークンそのものは, 書かない
                AppLog.w(TAG, "Could not decrypt the saved refresh token", e)
                null
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "The saved refresh token is broken", e)
                null
            }
        }
    }

    /**
     * 更新トークンを, 暗号化して保存する.
     *
     * @param token 更新トークン. null なら, 消す
     */
    override fun save(token: String?) {
        prefs.edit {
            if (token == null) {
                remove(PREF_KEY)
            } else {
                putString(PREF_KEY, Base64.getEncoder().encodeToString(encrypt(token)))
            }
        }
    }

    /**
     * 暗号化する (先頭に, 初期化ベクトルを付ける).
     *
     * @param plain 暗号化する文字列
     * @return 初期化ベクトルと, 暗号文
     */
    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    /**
     * 復号する.
     *
     * @param data 初期化ベクトルと, 暗号文
     * @return 復号した文字列
     * @throws GeneralSecurityException 復号できないとき
     */
    private fun decrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        return String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    }

    /**
     * 暗号化の鍵を返す (なければ, Android Keystore の中に作る).
     *
     * @return 鍵
     */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as SecretKey?) ?: createKey()
    }

    /**
     * Android Keystore の中に, 鍵を作る.
     *
     * @return 鍵
     */
    private fun createKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}
