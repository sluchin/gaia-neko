# 環境構築

gaia-neko の、ビルドとテストの手順です。作りは、[thermon](https://github.com/sluchin/thermon) の `SETUP.md` と同じです (Docker のイメージ、Gradle、CI)。

## 1. 必要なもの

- Docker (Docker Compose を含む)
- Git

Android Studio、JDK、Android SDK は、ホストに入れません (Docker のイメージの中にあります)。

## 2. ホストのユーザー ID を環境変数にエクスポートする

生成物 (`build/` など) が、root の所有にならないように、コンテナを、ホストのユーザーで実行します。

```bash
export UID GID=$(id -g)
```

## 3. コマンド

```bash
docker compose build                  # 開発用のイメージを作る (Dockerfile を変えたときも)
docker compose run --rm build         # デバッグ用の APK (app/build/outputs/apk/debug/app-debug.apk)
docker compose run --rm test          # 単体テスト
docker compose run --rm coverage      # core の行と分岐のカバレッジ 100%
docker compose run --rm format        # 整形の確認 (ktlint)
docker compose run --rm format-fix    # 整形して書き換える
docker compose run --rm analyze       # 静的解析 (detekt と Android Lint)
docker compose run --rm docs          # ドキュメント (Dokka)。core/build/dokka/html/index.html
docker compose run --rm clean         # 生成物を消す
docker compose run --rm dev           # 開発シェル
```

`docker compose run` には、必ず `--rm` を付けます。

## 4. AWS の設定 (これから)

接続先は、`local.properties` (コミットしない) か、Gradle のプロパティで渡します。

```properties
# local.properties (リポジトリの直下。.gitignore で無視する)
gaianeko.region=ap-northeast-1
gaianeko.userPoolId=<ユーザープール ID>
gaianeko.clientId=<アプリクライアント ID>
gaianeko.identityPoolId=<ID プール ID>
gaianeko.table=gaia-neko-sightings
gaianeko.bucket=<バケット名>
```

実際の ID を、リポジトリに書きません。AWS の準備の手順は、これから `AWS_SETUP.md` に書きます。

## 5. クラウドのセッション (claude.ai/code)

通常の `docker compose` は、プロキシの設定がないため、失敗します。`scripts/cloud-docker.sh build` と `scripts/cloud-docker.sh run <サービス>` を使います (詳しくは、`.claude/CLAUDE.md`)。

## 6. CI (GitHub Actions)

push のたびに、`.github/workflows/build.yml` が、`build` / `test` / `coverage` / `format` / `analyze` / `docs` を、実行します。`build` が成功すると、デバッグ用の APK を、アーティファクト `gaia_neko_apk` に保存します (3 日で期限が切れます。Actions の実行の画面から、ダウンロードできます)。
