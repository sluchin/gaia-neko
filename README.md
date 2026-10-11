# gaia-neko

Android app to report stray cat sightings with a photo and GPS location. Photos and locations are stored in AWS (S3 and DynamoDB) and are visible only to administrators.

外で見つけた猫を、利用者が撮影して、写真と GPS の位置を AWS に保存する Android アプリです。管理者が、アプリの中のマップで、猫の分布を見て、位置に紐づいた写真を取得します。

> **状態**: 雛形です (ビルドの土台と、`core` の位置と Exif の GPS の読み取り)。撮影、ログイン、AWS への保存、管理者のマップは、これから作ります。

## 設計の方針

**位置と写真は、管理者だけが見られます。** 誰でも見られると、猫を虐待しに行く人が出るためです。

- **利用者**: Cognito のユーザープールでログインします。写真と位置を、書き込めます。**読み取りは、できません** (自分の投稿も)。
- **管理者**: ユーザープールのグループ `admin` に入れた人です。マップで分布を見て、ピンから写真を取得します。
- **写真の本体は S3 に置く**: DynamoDB の 1 件は 400 KB までなので、写真 (数 MB) は入りません。DynamoDB には、位置、時刻、S3 のキーを保存します。
- **S3 は公開しない**: パブリックアクセスをブロックします。管理者にだけ、署名付きの URL で、写真を見せます。

```
利用者 ──ログイン──> Cognito ユーザープール ──> Cognito ID プール ──一時的な認証情報──┐
                                                                                      ▼
Android アプリ ─ 写真 ──────────────────────────────> S3 (gaia-neko 用のバケット)      │
               └ 位置・時刻・S3 のキー・利用者の ID ──> DynamoDB (gaia-neko-sightings)  │
管理者 (グループ admin) ──マップで分布を見る・写真を取得する (読み取りの権限は、管理者だけ) ◀┘
```

### 権限 (最小権限)

| 役割 | S3 | DynamoDB |
|:---|:---|:---|
| 利用者 | `PutObject` (自分の `photos/<自分の ID>/` だけ) | `PutItem` (パーティションキー `userId` が自分の分だけ) |
| 管理者 | `GetObject`、`DeleteObject`、`PutObject` (自分の `photos/<自分の ID>/` だけ) | `Query` / `Scan`、`DeleteItem`、`PutItem` (自分の分だけ) |

利用者のロールに、読み取りの権限を付けません。アクセスキーを、アプリやリポジトリに入れません (Cognito の一時的な認証情報だけを使います)。

### データ (DynamoDB のテーブル gaia-neko-sightings)

| 項目 | 内容 |
|:---|:---|
| `userId` | 撮影した利用者 (Cognito の ID プールの ID。パーティションキー。権限を、自分の分だけに絞るため) |
| `id` | 目撃の識別子 (UUID。ソートキー) |
| `latitude` / `longitude` | 位置 (度)。写真の Exif の GPS。なければ、撮影のときの端末の位置 |
| `takenAt` | 撮影した時刻 (UNIX 時刻 (s)) |
| `photoKey` | S3 のキー (`photos/<利用者の ID>/<id>.jpg`) |
| `photographer` | 撮影した利用者のメールアドレス (管理者が、誰の投稿かを知るための表示用。利用者が自分で書くので、信頼できる識別は `userId`。ない投稿もある) |

## 言語

画面は、端末の言語が日本語なら日本語、それ以外は英語で表示します (`app/src/main/res/values/strings.xml` と `values-ja/strings.xml`)。AWS が返すエラーのメッセージは、英語のままです。

## 作る順番

1. [x] ビルドの土台 (Docker、Gradle、CI、整形、静的解析、テスト、カバレッジ 100%。[thermon](https://github.com/sluchin/thermon) と同じ)
2. [x] `core`: 位置 (`GeoPoint`)、Exif の GPS の読み取り (`parseExifGps`)、目撃 (`Sighting`)
3. [x] AWS の準備 (ユーザープール、ID プール、バケット、テーブル、ロール) の手順 ([AWS_SETUP.md](AWS_SETUP.md))
4. [x] 利用者のログイン (Cognito)。`core`: `UserPoolAuth` (登録、確認、ログイン、更新)、`SessionManager` (ログインの状態。更新トークンで、開き直したときに、ログインを飛ばす)、`CognitoLoginCredentials` (ID プールの認証情報)、`Session.isAdmin` (管理者の判定)。`app`: ログインの画面 (メールアドレス、パスワード、登録、確認コード)。更新トークンは、Android Keystore の鍵で暗号化して保存する。実際の Cognito での動作は、`AWS_SETUP.md` の手順で AWS を作ってから、確認する
5. [x] 撮影と保存。`core`: `SightingUploader` (写真を S3 に先に保存して、目撃を DynamoDB に保存する)。`app`: カメラのアプリを起動して撮影、写真の Exif の GPS (なければ、端末の最後の位置)、位置の権限
6. [x] 管理者のマップ。`core`: `DynamoDbSightingSource` (全員の目撃を読む)、`S3PhotoSource` (写真を読む)、`boundsOf` (全てが見える範囲)。`app`: OpenStreetMap の地図 (osmdroid。API キーは要らない) に、ピンで表示して、同じ場所 (約 11 m 以内) の目撃は、1 つのピンにまとめる。ピンを押すと、写真、撮影日時、位置 (緯度、経度)、撮影者を表示して、左右にスワイプすると、同じ場所のほかの写真を見られる。写真を長押しすると、確認のあと、その写真と記録を削除する (管理者だけ)。管理者 (グループ `admin`) だけに、ボタンが出る。読み取りの権限は、IAM のロールが決める (利用者は、読めない)。実際の AWS、カメラ、地図の表示は、実機で確認する

## 開発環境

Android Studio は使いません。ビルド、テスト、整形、静的解析、ドキュメントの生成は、全て、コマンドラインで、Docker の中で実行します。手順は、[SETUP.md](SETUP.md) です。

```
core/    純粋な Kotlin (Android に依存しない). 単体テストで, 行と分岐のカバレッジ 100%
app/     Android アプリ. 画面と, 設定の読み込みだけの, 薄い層
config/  detekt (静的解析) の設定
gradle/  版カタログ (libs.versions.toml) と, Gradle ラッパー
```

## ライセンス

GPL-3.0-or-later ([LICENSE](LICENSE))。
