# プロジェクト設定

Kotlin で作る Android アプリ (gaia-neko)。外で見つけた猫を利用者が撮影して、写真と GPS の位置を AWS (S3 と DynamoDB) に保存する。管理者がアプリの中のマップで、猫の分布を見て、位置に紐づいた写真を取得する。

**位置と写真は管理者だけが見られる** (誰でも見られると、猫を虐待しに行く人が出るため)。利用者は書き込みだけができて、読み取りはできない。利用者は Cognito のユーザープールで認証する。管理者はユーザープールのグループ (`admin`) で決める。姉妹プロジェクト [thermon](https://github.com/sluchin/thermon) と、同じ AWS のアカウントを使う (作りも、thermon に合わせる)。

**Android Studio は使わない。** ビルド、テスト、整形、静的解析、ドキュメントの生成は全てコマンドラインで、Docker の中で実行する。環境構築の詳細と、検証の記録は `SETUP.md`。

## コマンド

> **クラウドのセッション (claude.ai/code。スマホのアプリを含む。環境変数 `CLAUDE_CODE_REMOTE` が `true`) では、下の `docker compose run --rm <サービス>` を使わず、`scripts/cloud-docker.sh run <サービス>` で実行する** (`build`、`test`、`coverage`、`format`、`format-fix`、`analyze`、`docs`)。通常の `docker compose` はプロキシの設定がないため、curl の終了コード 60 や、Maven Central の 429 で失敗する。詳しくは下の「クラウドのセッション (claude.ai/code) で Docker を使うとき」。

### Docker (推奨)
- `docker compose run` には必ず `--rm` を付ける (終わったコンテナを残さないため)。
- `UID` と `GID` をシェルでエクスポートしておく (生成物が root 所有にならないように。`SETUP.md` の「2. ホストのユーザー ID を環境変数にエクスポートする」)。
- イメージのビルド: `docker compose build` (`Dockerfile` を変えたときも)
- ビルド (デバッグ APK): `docker compose run --rm build` (`app/build/outputs/apk/debug/app-debug.apk`)
- 単体テスト: `docker compose run --rm test`
- カバレッジ (`core` の行と分岐が 100%): `docker compose run --rm coverage`
- 整形の確認: `docker compose run --rm format` / 整形して書き換える: `docker compose run --rm format-fix`
- 静的解析 (detekt と Android Lint): `docker compose run --rm analyze`
- ドキュメント (Dokka): `docker compose run --rm docs` (`core/build/dokka/html/index.html`)
- クリーン: `docker compose run --rm clean` (`build/`、`.gradle/`、`.kotlin/` を消す。Gradle のキャッシュは消さない)
- 開発シェル: `docker compose run --rm dev`
- エミュレータ (KVM。`export KVM_GID=$(getent group kvm | cut -d: -f3)` が要る。イメージは約 10.7 GB): `docker compose build emulator` → `docker compose up -d emulator` → `docker compose exec emulator scripts/emulator-wait.sh` → `docker compose exec emulator adb install -r app/build/outputs/apk/debug/app-debug.apk`。画面は <http://localhost:6080/vnc.html>、画像にするときは `adb exec-out screencap -p > build/emulator-screen.png`。止めるのは `docker compose down` (`SETUP.md` の「エミュレータ (Docker)」) (中で `./gradlew ...` を直接実行できる)

### 実機への転送 (ホストの adb)
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` (ホストに adb を入れる。`SETUP.md` の「実機へのインストール」と「アプリを動かして確認する」)

### クラウドのセッション (claude.ai/code) で Docker を使うとき

**通常の `docker compose build` と `docker compose run` は使わない** (curl の終了コード 60 で失敗する。理由は下)。代わりに、`scripts/cloud-docker.sh` を使う (`SETUP.md` の「クラウドのセッション」)。

```bash
scripts/cloud-docker.sh build          # 開発用のイメージ (gaia-neko-dev) を作る (プロキシと CA の設定を足す)
scripts/cloud-docker.sh run build      # docker compose run --rm build (test、coverage、format、analyze、docs も同じ)
scripts/cloud-docker.sh start          # Docker のデーモンを起動する (起動済みなら、何もしない)
```

- クラウドの VM には Docker (`dockerd`) が入っているが、デーモンは自動では起動しない。SessionStart フック (`.claude/settings.json`) がセッションの開始のときに、`scripts/cloud-docker.sh hook` で起動する (クラウドのときだけ)。起動していないときは `scripts/cloud-docker.sh start`。ログは `/tmp/dockerd.log`。
- VM の外への通信は全てAnthropic のプロキシを通る。通信できるドメインは環境の「Network access」で決まる (標準の Trusted の一覧にないドメインは「Custom」で足す。`dl.google.com` と `maven-central.storage-download.googleapis.com` が要る)。
- **curl の終了コード 60 の原因**: VM の通信はプロキシと、そのプロキシの CA 証明書を使うが、**コンテナはどちらも引き継がない**。
  - VM には `HTTPS_PROXY=http://127.0.0.1:<ポート>` が設定されている。**ポートはセッションの途中でも変わる** (例: 37451、45999) ので、固定で書かず、実行のたびに `$HTTPS_PROXY` から読む。`127.0.0.1` はコンテナの中では、コンテナ自身を指す。
  - CA 証明書は `/root/.ccr/ca-bundle.crt` にある (`SSL_CERT_FILE` などが指す)。VM のシステムの信頼ストアには入っていない。
- **`cloud-docker.sh` がしていること**: `Dockerfile` を一時的にコピーして (リポジトリは変えない)、CA を**証明書ごとに 1 ファイルに分けて**、信頼ストアに入れる行を足す (束を 1 ファイルのまま入れると、Java の信頼ストアには最初の 1 枚しか入らず、Gradle が PKIX のエラーで落ちる)。`--network host` を付け、プロキシを `ARG` で渡す (`ENV` には焼き込まない)。**プロキシは HTTPS (`curl`、Java) だけに渡し、`HTTP_PROXY` は渡さない** (`apt` は Ubuntu のミラーに HTTP で接続するので、`HTTP_PROXY` を渡すと、プロキシに拒否されて失敗した。渡さなければ、プロキシを通さずに、直接つながる)。`apt` がそれでも失敗するときは環境の「Allowed domains」に `archive.ubuntu.com` と `security.ubuntu.com` を足す。実行 (`run`) のときは `network_mode: host` と、プロキシの環境変数と、`JAVA_TOOL_OPTIONS` (Java のプロキシ) を足す。コンテナは root で動く (`build/` などは root 所有になる)。
- イメージに入った CA はセッションが変わって、プロキシの CA が変わったときは古くなる。TLS のエラーが出たら、`scripts/cloud-docker.sh build` で作り直す。
- **Maven Central の `429 Too Many Requests`** (共有の出口の IP が回数制限を受ける)。Google のミラー `maven-central.storage-download.googleapis.com` を先に参照する。`scripts/cloud-docker.sh run` がクラウドのときに、毎回、`scripts/maven-mirror.init.gradle.kts` を Gradle のホーム (`/var/cache/gaia-neko-gradle`。`GRADLE_HOME_DIR` で変えられる) の `init.d/` に置く (環境変数も、セットアップスクリプトも、要らない)。ミラーに届かないときは `WARNING` を出す (「Allowed domains」にミラーのドメインを足す)。初期化スクリプトを貼り付けで作ると、全角スペースが混ざって壊れたことがあるので、リポジトリのファイルにした。
- **Plugin Portal と Maven Central の順**: ミラーに成果物がないと、次の Maven Central が 429 を返し、Gradle は 429 で解決を打ち切る。ktlint のプラグインは Plugin Portal にしかないので、`scripts/maven-mirror.init.gradle.kts` でプラグインの解決の順を「ミラー、`google()`、Plugin Portal」にしている。
- **Docker Hub の `429`** (ベースのイメージ `ubuntu:24.04` の取得): `scripts/cloud-docker.sh build` が失敗したときは Google のミラー `mirror.gcr.io/library/ubuntu:24.04` から取って、`ubuntu:24.04` のタグを付けてから、ビルドする (スクリプトが自動でする。)。
- 同じ失敗を繰り返さない。Docker を使わずに、VM の上で、直接ビルドする方法も、ある。

## プロジェクト構成

- `core/`: 純粋な Kotlin (Android に依存しない)。猫の目撃 (`Sighting`)、位置 (`GeoPoint`)、Exif の GPS の読み取り (`parseExifGps`)、ユーザープールの登録とログイン (`UserPoolAuth`)、ID プールの認証情報 (`CognitoLoginCredentials`)、ログインの状態と更新トークンの保存の入り口 (`SessionManager`、`RefreshTokenStore`)、目撃の保存 (`SightingUploader`) と読み出し (`SightingSource`、`PhotoSource`。管理者だけ)、地図の表示範囲 (`boundsOf`)、里親募集の一覧のページの解析 (`parseAdoptionPage`。jsoup) と、新しい猫の判定 (`newAdoptionCats`)、トークン (`Session`。管理者かの判定 `isAdmin` は画面の出し分けだけに使う)。AWS のクライアントは引数で渡して、テストでは、偽物にする。**単体テストで、行と分岐のカバレッジ 100%。**
- `app/`: Android。画面 (ログイン、これから: 撮影、管理者のマップ)。更新トークンは `SecureRefreshTokenStore` (Android Keystore の鍵で暗号化) に保存する。画面 (`MainActivity`、`LoginScreen`、`HomeScreen`、管理者の地図 `AdminMapScreen`)。画面は Material Components の View (Compose は使わない。部品を作る関数は `Views.kt`)。地図のピンには、同じ場所の写真の枚数を書く (`PinIcon.kt`)。ログインのあとは、下のタブ (`HomeTabs`) で、撮影と里親募集 (`AdoptionScreen`) を切り替える。地図は osmdroid (OpenStreetMap)。OpenStreetMap のタイルサーバーには利用ポリシーがある (大量のアクセスを避ける。`userAgentValue` を設定する) と、設定の読み込み (`BuildConfig`) だけの、薄い層。ロジックは `core` に置く (UI は単体テストの対象外)。
- `gradle/libs.versions.toml`: ライブラリとプラグインの版 (版カタログ)。版を変えるときはここだけ。
- `config/detekt/detekt.yml`: detekt の規則 (既定に上書きするものだけ)。
- `Dockerfile` (2 段: dev と emulator) / `docker-compose.yml` / `scripts/`: 開発環境 (thermon と同じ)。`.github/`: GitHub Actions。`SETUP.md`: 環境構築。`README.md`: 設計。

## AWS の設定と、秘密情報

- 接続先 (`gaianeko.region`、`gaianeko.userPoolId`、`gaianeko.clientId`、`gaianeko.identityPoolId`、`gaianeko.table`、`gaianeko.bucket`) は `local.properties` (コミットしない) か、Gradle のプロパティ (`-Pgaianeko.table=...`) で渡す。リポジトリに実際の ID (アカウント ID、ユーザープール ID、ID プール ID など) や、実際の位置、写真を書かない (文書の例はダミーの値にする)。
- **アクセスキーをアプリや、リポジトリに入れない。** 必ずCognito の一時的な認証情報を使う。署名の鍵 (`*.jks`、`*.keystore`) も、コミットしない。
- **権限の方針 (最小権限)**: 認証した利用者のロールは S3 の写真の書き込み (`s3:PutObject`。自分の `photos/<自分の ID>/` だけ) と、DynamoDB の書き込み (`dynamodb:PutItem`) だけ。**読み取りの権限を付けない。** 管理者のグループのロールだけが、DynamoDB の `Query` / `Scan` / `DeleteItem` と、S3 の `GetObject` / `DeleteObject` を持つ (管理者は利用者のロールではなく、管理者のロールだけになるので、管理者のロールにも、利用者と同じ書き込み (自分の分だけ) を付ける)。S3 のバケットは公開しない (パブリックアクセスのブロック)。
- **位置と写真をログや、エラーのメッセージに出さない。** テストや文書の例の位置は実在の場所を避けた、ダミーの値にする。

## メニューと設定

- ログインしたあとの画面は、`HomeDrawer` (DrawerLayout と NavigationView。`androidx.drawerlayout`) で包む。上のバーの三本線は `ActionBarDrawerToggle`。ログインや地図の画面に切り替えるときは、`detach()` で、三本線と、戻るの操作を外す。
- 画面は、上のバーの下まで広がる (edge-to-edge) ので、ドロワーにも、中身 (`HomeTabs`) と同じく、システムバーの上の余白を空ける (空けないと、見出しが上のバーに隠れる)。
- 設定は、別の画面 (`SettingsActivity`。上の矢印と、戻るで閉じる)。保存したデータ (`SavedData`。猫の一覧の JSON と、写真のファイル) の容量の表示と削除。削除したら `RESULT_OK` を返し、`MainActivity` が、里親募集の一覧 (`AdoptionScreen.forget()`) と、メモリの写真 (`ImageLoader.clearMemory()`) を捨てる。写真のフォルダは、消さずに、中のファイルだけを消す (`ImageLoader` が、そのフォルダに書き続けるため)。

## 里親募集 (ニャン友ねっとわーく北海道)

- 里親募集のタブは、[ニャン友ねっとわーく北海道](https://nyantomo.jp/) の里親募集の一覧 (`https://nyantomo.jp/satooya/`。固定ページに転送される) を、カードで並べる。カードを押すと、サイトの詳しいページを、ブラウザで開く。
- `robots.txt` は、全てを許可している (2026-10-17 に確認)。利用規約と、写真の転載の決まりは、サイトに書かれていない。
- **仕様は `ADOPTION.md`** (取得のタイミング、保存、新しい猫の探し方、全てのページの取得、更新の操作、失敗、削除、まだ反映しないもの)。里親募集の動きを変えたら、`ADOPTION.md` も直す。
- **サイトに負荷をかけない**: 取得するのは、起動してからタブを初めて開いたときと、更新 (更新のボタンと、引き下げ。`SwipeRefreshLayout`) と、設定で削除したあとだけ。普段は、新しい猫を探すだけ (1 ページ目から、新しい猫のいないページで止める。起動後の最初の表示では、足す前に、ダイアログで聞く)。全てのページは、保存した一覧がないときだけ、1 ページずつ順に取得する。写真は、同時に 2 つまで取得して、端末のキャッシュに残す (`ImageLoader`)。名乗る User-Agent (`gaia-neko/0.1 (+https://github.com/sluchin/gaia-neko)`) を付ける (`SiteFetch.kt`)。
- お気に入りは、`filesDir` の `adoption-favorites.json` (`Favorites`。猫の情報ごと保存する。設定の削除では消さない)。カードと一覧は、里親募集とお気に入りで共通 (`CatCards.kt` の `catGrid`)。起動してから初めてお気に入りのタブを開いたときに、猫ごとに詳しいページを取得して、募集が終わった (404、410、状態が「里親募集中」でない) 猫を、灰色にして「譲渡済み」を付ける (`Favorites.checkOnce`、`adoption-favorites-closed.json`)。
- 猫の一覧は、`filesDir` の `adoption-cats.json` (`CatStore`。キャッシュのフォルダは、システムに消されることがあるので、使わない。ページの HTML は、ページの区切りがずれるので、保存しない)。
- **サイトの写真と文章を、リポジトリにも、アプリにも、含めない**: 写真は、実行のときに、サイトの URL から取得する。テストの HTML は、サイトと同じ作りの、ダミーにする (実在の猫の名前や写真を書かない)。
- 開発者は、ニャン友ねっとわーく北海道の関係者で、アプリで一覧と写真を表示することに、問題はない (2026-10-17 に確認)。写真の権利は、ニャン友ねっとわーく北海道にある。
- サイトは WordPress (Elementor と JetEngine)。里親募集は REST API に出ていないので、HTML を解析する。ページ送りは JetSmartFilters で、次のページは、一覧の固定ページ (`canonical`) に `?jsf=jet-engine&pagenum=N` を付けた URL (`/satooya/` に付けると、転送されず、空の一覧になる)。今のページとページの数は、一覧の部品の `data-page` と `data-pages`。全ての猫の数は、ページの中のスクリプトの `"found_posts":263` (進み具合の表示に使う)。一覧の部品 (`.jet-listing-grid__items`) が見つからなければ、`AdoptionParseException` で失敗にする (サイトの作りが変わったことが、画面のエラーで分かる)。

## コーディング規約

ktlint (Kotlin 公式のスタイル) と、detekt (既定の規則) に従う。迷ったら、ツールが正しい。Kotlin 公式のコーディング規約へのリンクは `CODING_STYLE.md`。

- **インデントは空白 4 つ。1 行は 100 桁まで** (`.editorconfig`)。整形は `format-fix` が行う (手で、整形しない)。
- **ファイルの先頭に著作権とライセンスを書く**:

  ```kotlin
  /*
   * Copyright (c) 2026 Tetsuya Higashi
   *
   * SPDX-License-Identifier: GPL-3.0-or-later
   */
  ```
- **画面の文字列は多言語にする**: 文字列はコードに書かず、`app/src/main/res/values/strings.xml` (英語。既定) と `values-ja/strings.xml` (日本語) に同じ名前で書く。端末の言語が日本語なら、日本語で、それ以外は英語で表示する。文字列を足したら、両方に足す (足りないと、Android Lint の `MissingTranslation` で `analyze` が失敗する)。画面の日本語の句読点はふつうの「、」と「。」でよい (ソースのコメントの句読点とは別)。AWS や Cognito が返すエラーのメッセージは英語のまま表示する。
- **コメントと KDoc は日本語。** 句読点は「, 」(カンマと空白) と「.」(ピリオド) にする (「、」と「。」は使わない)。ログのメッセージ、例外のメッセージ、識別子は英語。
- **公開 (public) の宣言には KDoc を付ける** (`docs` が `Undocumented` の警告で失敗する)。`@param`、`@return`、`@throws` を書く。`private` にも、意味と単位が分かる説明を付ける。
- **KDoc の中で、`[s]` や `[%]` のように、角括弧で単位を書かない** (リンクとして解釈されて、`docs` が失敗する)。`(s)` や `(%)` と書く。リンクにしたいときだけ、`[クラス名]` と書く。
- **マジックナンバーを使わない** (detekt の `MagicNumber`)。時間、倍率、上限などは `private const val` で名前を付ける。
- **警告をゼロにする。** Kotlin の警告はエラーになる (`allWarningsAsErrors`)。Android Lint も、警告をエラーにする。警告を抑える (`@Suppress`、`lint { disable }`) ときは理由をコメントに書く。
- **例外**: 汎用の例外 (`Exception`、`Throwable`) を握りつぶさない。AWS SDK の失敗は呼び出し元に伝える。画面で、まとめて、扱うときは `runCatching` でメッセージを表示する。
- **AWS SDK のクライアントは `core` の関数やクラスに引数で渡す** (テストで、偽物に置き換えるため)。時刻など、外の状態を使うものも、引数 (関数) で渡す。
- **秘密情報や、環境固有の値をコードに書かない** (上の「AWS の設定と、秘密情報」)。
- 変更しない値は `val` にする (`var` は必要なときだけ)。`!!` はテスト以外で、使わない。

## 開発フロー

1. Push 時に GitHub Actions (`.github/workflows/build.yml`) が開発用の Docker イメージ (ローカルと同じ環境) で `docker compose` のサービスを実行する (`SETUP.md` の「CI (GitHub Actions)」)。
   - `check` ジョブ: `build` / `test` / `coverage` / `format` / `analyze` をサービスごとに実行する。失敗したら、ジョブが失敗する。実行のログをアーティファクト (`<サービス名>_log`。`build_log` など) として保存する (失敗したときも保存する)。`build` のときはデバッグ用の APK (`app/build/outputs/apk/debug/app-debug.apk`。デバッグ鍵で署名) も、アーティファクト `gaia_neko_apk` として保存する (成功したときだけ)。リリース用の署名の鍵 (`*.jks`、`*.keystore`) はリポジトリにも、CI にも、置かない。
   - `docs` ジョブ: `docs` (Dokka の HTML。警告があれば失敗する)。生成したドキュメントをアーティファクト `docs` と、ログ `docs_log` に保存する。
   - `deploy-docs` ジョブ: main への push のときだけ、ドキュメントを GitHub Pages に公開する (リポジトリの Settings > Pages > Source を「GitHub Actions」にしておく)。
   - `cleanup` ジョブ: 3 日より前の実行 (ログと artifact) を push のたびに削除する (権限は `actions: write`)。artifact は `retention-days: 3` で 3 日で期限が切れる。保存する期間を変えるときは `build.yml` の `KEEP_DAYS` と、全ての `retention-days` をそろえて変える。
   - Docker イメージはレイヤーを GitHub Actions のキャッシュに保存する (`.github/actions/docker-image`)。Gradle の依存関係も、キャッシュに保存する (`.github/actions/gradle-cache`。`GRADLE_HOME_DIR`)。
   - アーティファクトの名前は小文字のスネークケースにする。

### コード修正時のルール

- クラウドのセッションでは、次の `docker compose run --rm <サービス>` を `scripts/cloud-docker.sh run <サービス>` に読み替える。
- コードを修正したら、次の順に全て成功することを確認する。**どれも、警告を出さない。** 警告やエラーが出たら修正して、出なくなるまで繰り返す。
  1. `docker compose run --rm format-fix` (整形。自動で直せない指摘があると、終了コード 1 になる)
  2. `docker compose run --rm format` (整形の確認)
  3. `docker compose run --rm analyze` (静的解析)
  4. `docker compose run --rm test` (単体テスト)
  5. `docker compose run --rm coverage` (`core` の行と分岐が 100%)
  6. `docker compose run --rm build` (APK のビルド)
  7. `docker compose run --rm docs` (ドキュメント。KDoc を変えたときは必ず)
- ビルドや、テストを実行できなかったときはそのことを報告する (成功したとは書かない)。
- **例外**: 変更が `SETUP.md`、`README.md`、`.claude/CLAUDE.md` などの文書だけの場合はビルドしない。
- ビルドやコマンド、ツールの版を変えたら、`SETUP.md` も更新する (版は実際に使うものだけを書く)。
- ファイルを追加したら、Gradle の設定 (`build.gradle.kts`) と、必要なら、`SETUP.md` の構成も更新する。

### タスク完了時の動作

- 修正が完了し、全ての確認が成功したら、`git diff --stat` で差分を確認し、変更点と理由の簡潔なまとめをユーザーに報告する。
- ユーザーに依頼されない限り、`git commit` / `git push` は行わない。

## テスト

単体テストは JVM 上で実行する (JUnit 5 と `kotlin-test`)。AWS SDK のクライアント (Kotlin のインターフェース) は MockK で偽物にする。中断関数 (`suspend`) は `kotlinx-coroutines-test` の `runTest` でテストする。エミュレータや、実機での自動テスト (instrumented test) は使わない。

- **配置**: `core/src/test/kotlin/` (テスト対象と、同じパッケージ)。クラス名は `<対象>Test`。`app` にはテストを置かない (薄い層にする)。
- **カバレッジ**: `core` の行と分岐は 100% (JaCoCo の `jacocoTestCoverageVerification`)。ソースを足したり、関数を変えたりしたら、対応するテストを足す (正常系と、エラーの伝わり方)。到達しない分岐を作らない (到達しない `?: return` などを書かない)。
- **テストの名前**: `<対象の関数名><条件>` をキャメルケースで (例: `resolveFailsWithoutIdentityId`)。KDoc で何を確認するかを書く。
- **時刻と乱数**: 外の状態は引数で渡して、固定する (`now` を関数で渡すように)。実際の時計を使う動作は別に 1 つだけ、テストする。
- **実機と AWS での確認は別に必要**: 実際の AWS への接続 (ユーザープール、ID プール、テーブル、バケット、権限)、カメラと GPS、マップの表示、実機へのインストールは単体テストでは、確認できない (画面を変えたら、実機でも見る)。

## 落とし穴 (検証で分かったこと。詳細は SETUP.md)

- `compileSdk` は 37 以上 (AWS SDK が使う OkHttp 5.5 の要求)。`buildToolsVersion` は `37.0.0` に固定する (コンテナの SDK は読み取り専用)。足りない SDK のコンポーネントは `Dockerfile` に足して、イメージを作り直す。
- 配色は `res/values/colors.xml` と `res/values-night/colors.xml` (アイコンの青緑と橙。ライトとダーク)。Material の部品は `Theme.GaiaNeko` (`Theme.Material3.DayNight`) と `AppCompatActivity` で使う。テーマの色を、コードで読むときは `MaterialColors.getColor` (`colorPrimary` は `androidx.appcompat.R.attr`、そのほかは `com.google.android.material.R.attr`)。
- 文字だけのボタンは `androidx.appcompat.R.attr.borderlessButtonStyle` (Material 3 の TextButton) を使う (`android.R.attr.borderlessButtonStyle` は、古い見た目で、大文字になり、アイコンの色が付かない)。`MaterialButton` に `setTextAppearance` を使うと、文字の色も変わるので、ボタンの色 (`textColors`) を戻す。
- AppCompat は core-ktx を連れてくるので、Android Lint の `UseKtx` が、KTX の関数 (`SharedPreferences.edit { }`、`createBitmap`、`toDrawable` など) を求める。
- `.gitignore` は `*.jar` を無視する。Gradle ラッパーの `gradle-wrapper.jar` だけ、例外にしてある。
- AWS SDK の `getId { }` などの DSL は拡張関数なので、`import aws.sdk.kotlin.services.cognitoidentity.getId` のように、インポートが要る。
- Cognito や DynamoDB の DSL (`QueryRequest { }` など) の中では、`inline` でない関数 (`joinToString` など) の引数の中から、中断関数を呼べない。`map` などの `inline` の関数を使う。
- 未使用の変数は Kotlin のコンパイラの警告にならない。
- ktlint の `format-fix` は 1 回目に自動で直せない指摘があると、終了コード 1 で終わる。直したあとに、`format` で確認する。
- 画面 (`app`) を作るときはシステムバーの余白 (edge-to-edge。`targetSdk` 35 以上で必須) をパディングに入れる (入れないと、文字がアクションバーの下に隠れる)。画面はエミュレータで、確認する (`adb exec-out screencap -p` で画像にして、見られる)。
- コンテナの `/dev/kvm` は `kvm` グループ専用 (`KVM_GID`)。デバッグ用の署名の鍵はボリュームに残す (`ANDROID_USER_HOME`)。残さないと、`INSTALL_FAILED_UPDATE_INCOMPATIBLE` になる。
- エミュレータは起動の直後に重く、`System UI isn't responding` が出ることがある (`scripts/emulator-wait.sh` で落ち着くまで待つ)。
- ログの日本語が `????` と化けるときはコンテナの `LANG` (`Dockerfile` の `C.UTF-8`) を確認する。

## コミットガイドライン

- コミットメッセージは英語で記述
- **件名は Conventional Commits の形式で書く**: `type(scope): description`。scope (括弧を含む) は省略できる。description は英語で、命令形 (例: `feat(core): Add the parser of the GPS in Exif`)。
  - type は次のどれか。
    - `feat`: 機能の追加
    - `fix`: バグの修正
    - `docs`: 文書とコメントだけの変更 (`*.md`、KDoc)
    - `style`: 整形、規約の適用 (動作を変えない)
    - `refactor`: 動作を変えない、書き換え
    - `test`: テストだけの変更
    - `build`: ビルドと、開発環境 (`build.gradle.kts`、`gradle/libs.versions.toml`、`Dockerfile`、`docker-compose.yml`、Gradle ラッパー) の変更
    - `ci`: CI (`.github/`) の変更
    - `chore`: そのほかの雑務 (最初のコミット、`.gitignore` など)
  - scope は変更の場所: `core` / `app` (そのモジュールだけ)、`docker`、`gradle`。両方のモジュールにまたがるときは省略する。
  - 互換性を壊す変更は type (または scope) の直後に感嘆符を付ける (Conventional Commits の仕様のとおり)。
  - 複数の type にまたがるときは変更の中心になる type にする。
- 件名のあとに空行を 1 行入れて、本文を書く。本文はダッシュ/ハイフンで始まる箇条書き (例: `- Fix bug in parser`)
- コミットメッセージに `Co-Authored-By` または Claude 帰属行を含めない
- **rebase したあとの push は `git push --force-with-lease --force-if-includes origin <ブランチ名>` で行う** (`--force` は使わない。リモートに手元に取り込んでいないコミットがあれば、push は失敗する)。ユーザーに依頼されたときだけ、push する。
