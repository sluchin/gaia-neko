import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// AWS の設定 (リージョン, ユーザープール, ID プール, テーブル名, バケット名) は, リポジトリに入れない.
// local.properties (コミットしない. UTF-8) か, Gradle のプロパティ (-Pgaianeko.table=...) で渡す.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.reader(Charsets.UTF_8).use { load(it) }
        }
    }

/** 設定の値を, local.properties, Gradle のプロパティ, 既定値の順に探す. */
fun configValue(
    name: String,
    default: String,
): String = localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: default

/** BuildConfig に書く, 文字列の値 (引用符を付ける). */
fun configString(
    name: String,
    default: String,
): String = "\"" + configValue(name, default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Kotlin の警告が出たら, ビルドを失敗させる (core と同じ)
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

android {
    namespace = "jp.gaianeko.app"
    // compileSdk 37 は, AWS SDK for Kotlin が使う OkHttp 5.5 の要求 (AWS SDK を使うときに要る). build-tools は, Docker のイメージに
    // 入っている版に固定する (SDK は読み取り専用で, 既定の版 (36.0.0) は, 入っていない)
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // applicationId は, 仮の値 (リリースの前に決める)
        applicationId = "jp.gaianeko.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // AWS の設定 (README / SETUP.md の「AWS への接続」を参照)
        buildConfigField("String", "AWS_REGION", configString("gaianeko.region", "ap-northeast-1"))
        buildConfigField("String", "USER_POOL_ID", configString("gaianeko.userPoolId", ""))
        buildConfigField("String", "CLIENT_ID", configString("gaianeko.clientId", ""))
        buildConfigField("String", "IDENTITY_POOL_ID", configString("gaianeko.identityPoolId", ""))
        buildConfigField(
            "String",
            "TABLE_NAME",
            configString("gaianeko.table", "gaia-neko-sightings"),
        )
        buildConfigField("String", "BUCKET_NAME", configString("gaianeko.bucket", ""))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // 警告も, エラーにする
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))
    // Dispatchers.Main (画面のスレッド)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
