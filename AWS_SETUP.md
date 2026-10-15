# AWS の準備

gaia-neko が使う AWS のリソースを AWS CLI で作る手順です。AWS CLI の準備 (IAM ユーザーとアクセスキー、または `aws login`) は [thermon の AWS_CLI.md](https://github.com/sluchin/thermon/blob/main/AWS_CLI.md) と同じです。

> **注意**: この手順は AWS のドキュメントに基づいています。AWS の画面、料金、サービスの提供状況は変わることがあるので、最新の情報を確認してください。コマンドの結果が違うときはエラーのメッセージを見て、直してください。

## 1. 作るもの

| リソース | 名前 | 役割 |
|:---|:---|:---|
| DynamoDB のテーブル | `gaia-neko-sightings` | 位置、時刻、写真のキー |
| S3 のバケット | `gaia-neko-photos-<アカウント ID>` | 写真の本体 (公開しない) |
| Cognito のユーザープール | `gaia-neko` | 利用者のログイン (メールアドレス) |
| Cognito のユーザープールのグループ | `admin` | 管理者 |
| Cognito の ID プール | `gaia_neko` | ログインした利用者に AWS の一時的な認証情報を渡す |
| IAM のロール | `gaia-neko-user` / `gaia-neko-admin` | 利用者 (書き込みだけ) と管理者 (読み取り) |

**位置と写真は管理者だけが見られます。** 利用者のロールには読み取りの権限を付けません。

## 2. 共通の変数

以降のコマンドは同じシェルで、続けて実行します (シェルを閉じたら、この章と、4 章以降の変数を設定し直します)。アカウント ID はコマンドで取り出して、変数に入れるので、ファイルや文書に書き写しません。

```bash
export AWS_REGION=ap-northeast-1
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET="gaia-neko-photos-${ACCOUNT_ID}"
TABLE=gaia-neko-sightings
echo "$ACCOUNT_ID $BUCKET"
```

- 以降で作る JSON ファイルはカレントディレクトリに `aws-*.json` の名前で作ります (アカウント ID などが入ります)。リポジトリの外 (`~/tmp` など) で実行すると、ファイルが残りません。リポジトリの中で実行したときは `.gitignore` が `/aws-*.json` を無視します。**コミットしません。**

## 3. DynamoDB のテーブルと S3 のバケット

```bash
# テーブル: パーティションキー userId (利用者の ID), ソートキー id (目撃の ID)
aws dynamodb create-table --table-name "$TABLE" \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=id,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH AttributeName=id,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

# バケット: 公開しない
aws s3api create-bucket --bucket "$BUCKET" \
  --create-bucket-configuration LocationConstraint=ap-northeast-1
aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

- キーを `userId` と `id` にするのは利用者の書き込みを**自分のパーティションだけ**に絞るためです (IAM の `dynamodb:LeadingKeys`。5 章)。
- `userId` は Cognito の ID プールの ID (`ap-northeast-1:` から始まる文字列) です。ユーザープールの `sub` ではありません (IAM の条件で使えるのが、ID プールの ID なので)。
- バケット名は世界で 1 つだけです。すでに使われていると、失敗します (`BucketAlreadyExists`)。そのときは `BUCKET` を変えます。
- バケットの暗号化は既定で、S3 が管理する鍵 (SSE-S3) です。

## 4. Cognito のユーザープールと、ID プール

```bash
# ユーザープール: メールアドレスでログインする. 利用者が, 自分で登録できる (メールの確認コード)
USER_POOL_ID=$(aws cognito-idp create-user-pool --pool-name gaia-neko \
  --username-attributes email --auto-verified-attributes email \
  --policies 'PasswordPolicy={MinimumLength=10,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
  --query UserPool.Id --output text)

# アプリのクライアント (秘密のキーなし. アプリに秘密を入れられないため)
CLIENT_ID=$(aws cognito-idp create-user-pool-client --user-pool-id "$USER_POOL_ID" \
  --client-name gaia-neko-app --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --query UserPoolClient.ClientId --output text)

# ID プール: ログインした利用者だけ (未認証は, 許可しない)
IDENTITY_POOL_ID=$(aws cognito-identity create-identity-pool --identity-pool-name gaia_neko \
  --no-allow-unauthenticated-identities \
  --cognito-identity-providers \
  "ProviderName=cognito-idp.ap-northeast-1.amazonaws.com/${USER_POOL_ID},ClientId=${CLIENT_ID},ServerSideTokenCheck=true" \
  --query IdentityPoolId --output text)
echo "$USER_POOL_ID $CLIENT_ID $IDENTITY_POOL_ID"
```

- `ALLOW_USER_PASSWORD_AUTH` はパスワードを TLS (HTTPS) の上で、そのまま送る方式です。アプリの最初の版を簡単にするために選びました。SRP (パスワードを送らない方式) は AWS SDK for Kotlin に計算の部品がないので、あとで、必要なら、別に作ります。
- **ID はあとで `local.properties` に書きます (7 章)。リポジトリには書きません。**

## 5. IAM のロール (利用者と管理者)

ID プールが渡す一時的な認証情報は IAM のロールで、権限が決まります。ログインした人がグループ `admin` に入っていれば、管理者のロール、入っていなければ、利用者のロールになります。

### 5.1 信頼ポリシー (両方のロールに共通)

「この ID プールで、ログインした人だけが、このロールを使える」という条件です。

```bash
cat > aws-trust.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Federated": "cognito-identity.amazonaws.com" },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": { "cognito-identity.amazonaws.com:aud": "${IDENTITY_POOL_ID}" },
        "ForAnyValue:StringLike": { "cognito-identity.amazonaws.com:amr": "authenticated" }
      }
    }
  ]
}
EOF
```

### 5.2 利用者のロール (書き込みだけ)

```bash
cat > aws-user.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PutOwnPhoto",
      "Effect": "Allow",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::${BUCKET}/photos/\${cognito-identity.amazonaws.com:sub}/*"
    },
    {
      "Sid": "PutOwnSighting",
      "Effect": "Allow",
      "Action": "dynamodb:PutItem",
      "Resource": "arn:aws:dynamodb:ap-northeast-1:${ACCOUNT_ID}:table/${TABLE}",
      "Condition": {
        "ForAllValues:StringEquals": {
          "dynamodb:LeadingKeys": [ "\${cognito-identity.amazonaws.com:sub}" ]
        }
      }
    }
  ]
}
EOF

aws iam create-role --role-name gaia-neko-user \
  --assume-role-policy-document file://aws-trust.json
aws iam put-role-policy --role-name gaia-neko-user --policy-name put-own \
  --policy-document file://aws-user.json
```

- `\${cognito-identity.amazonaws.com:sub}` は IAM の変数で、ログインした本人の ID プールの ID に置き換わります (シェルでは、`\` を付けて、そのまま JSON に書きます)。
- 利用者は**自分の** `photos/<自分の ID>/` の下にだけ、写真を置けて、**自分の** `userId` のパーティションにだけ、書けます。**読み取り (`GetObject`、`Query`、`Scan`) の権限はありません。**

### 5.3 管理者のロール (読み取り、削除、自分の投稿)

```bash
cat > aws-admin.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadSightings",
      "Effect": "Allow",
      "Action": [ "dynamodb:Scan", "dynamodb:Query", "dynamodb:GetItem", "dynamodb:DeleteItem" ],
      "Resource": "arn:aws:dynamodb:ap-northeast-1:${ACCOUNT_ID}:table/${TABLE}"
    },
    {
      "Sid": "GetAndDeletePhotos",
      "Effect": "Allow",
      "Action": [ "s3:GetObject", "s3:DeleteObject" ],
      "Resource": "arn:aws:s3:::${BUCKET}/photos/*"
    }
  ]
}
EOF

aws iam create-role --role-name gaia-neko-admin \
  --assume-role-policy-document file://aws-trust.json
aws iam put-role-policy --role-name gaia-neko-admin --policy-name read-all \
  --policy-document file://aws-admin.json

# 管理者も, 猫を撮影して, 投稿する (利用者と同じ書き込みの権限: 5.2 の aws-user.json)
aws iam put-role-policy --role-name gaia-neko-admin --policy-name put-own \
  --policy-document file://aws-user.json
```

- 管理者はグループ `admin` に入ると、**利用者のロールではなく、管理者のロールだけ**になります (ロールは 1 つだけ引き受けるため)。管理者も、猫を撮影して、投稿できるように、管理者のロールにも、利用者と同じ書き込みの権限 (`put-own`) を付けます。付けないと、管理者の投稿が `s3:PutObject` の `AccessDenied` になります。

- 管理者は全員の目撃 (`Scan`) と、写真 (`GetObject`) を読めます。また、目撃 (`DeleteItem`) と、写真 (`DeleteObject`) を削除できます (アプリで、地図のピンの写真を長押しして、削除します)。書き込みは利用者と同じく、自分の分だけです (`put-own`)。写真はアプリが S3 から直接ダウンロードして、表示します (署名付き URL は使いません)。

## 6. ID プールにロールを結びつけて、管理者のグループを作る

```bash
USER_ROLE_ARN=$(aws iam get-role --role-name gaia-neko-user --query Role.Arn --output text)
ADMIN_ROLE_ARN=$(aws iam get-role --role-name gaia-neko-admin --query Role.Arn --output text)

# グループ admin に, 管理者のロールを結びつける (ロールの引き受けは, この結びつけで決まる)
aws cognito-idp create-group --user-pool-id "$USER_POOL_ID" --group-name admin \
  --role-arn "$ADMIN_ROLE_ARN" --precedence 0

# ID プール: ふつうは利用者のロール. グループにロールがあれば, そのロール (Token)
aws cognito-identity set-identity-pool-roles --identity-pool-id "$IDENTITY_POOL_ID" \
  --roles "authenticated=${USER_ROLE_ARN}" \
  --role-mappings "{\"cognito-idp.ap-northeast-1.amazonaws.com/${USER_POOL_ID}:${CLIENT_ID}\":{\"Type\":\"Token\",\"AmbiguousRoleResolution\":\"AuthenticatedRole\"}}"
```

- ログインした人がグループ `admin` に入っていれば、管理者のロール、入っていなければ、利用者のロール (`authenticated`) になります。
- **管理者は少人数に絞ってください。** グループに入れた人は全員の位置と写真を読めます。

## 7. 設定をアプリに渡す

```bash
echo "gaianeko.userPoolId=${USER_POOL_ID}"
echo "gaianeko.clientId=${CLIENT_ID}"
echo "gaianeko.identityPoolId=${IDENTITY_POOL_ID}"
echo "gaianeko.table=${TABLE}"
echo "gaianeko.bucket=${BUCKET}"
```

出力をリポジトリの直下の `local.properties` に書きます (`.gitignore` で無視します。**コミットしません**)。

```properties
gaianeko.region=ap-northeast-1
gaianeko.userPoolId=<出力の値>
gaianeko.clientId=<出力の値>
gaianeko.identityPoolId=<出力の値>
gaianeko.table=gaia-neko-sightings
gaianeko.bucket=<出力の値>
```

## 8. 管理者を作る

利用者はアプリで、自分で登録します (メールの確認コード)。管理者は登録したあとに、グループに入れます。

```bash
# 例: 自分のメールアドレスで, 管理者を, 直接作る (一時パスワードを, 本物のパスワードにする)
EMAIL=you@example.com
aws cognito-idp admin-create-user --user-pool-id "$USER_POOL_ID" --username "$EMAIL" \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true \
  --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id "$USER_POOL_ID" --username "$EMAIL" \
  --password '<10 文字以上の, 大文字, 小文字, 数字を含むパスワード>' --permanent
aws cognito-idp admin-add-user-to-group --user-pool-id "$USER_POOL_ID" --username "$EMAIL" \
  --group-name admin
```

- `you@example.com` と、パスワードは自分のものに、置き換えます。パスワードをシェルの履歴や、チャットに残さないでください。
- 登録済みの利用者を管理者にするときは `admin-add-user-to-group` だけを実行します。
- 管理者から外すときは `admin-remove-user-from-group` です。

## 9. 権限を試す (任意)

アプリを作る前に利用者のロールが書き込みだけで、読み取りできないことを CLI で試せます。

```bash
# 1. ログインして, ID トークンを得る (利用者のメールアドレスとパスワード)
ID_TOKEN=$(aws cognito-idp initiate-auth --client-id "$CLIENT_ID" --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters USERNAME="$EMAIL",PASSWORD='<パスワード>' \
  --query AuthenticationResult.IdToken --output text)

# 2. ID プールの ID と, 一時的な認証情報を得る
LOGIN="cognito-idp.ap-northeast-1.amazonaws.com/${USER_POOL_ID}=${ID_TOKEN}"
IDENTITY_ID=$(aws cognito-identity get-id --identity-pool-id "$IDENTITY_POOL_ID" \
  --logins "$LOGIN" --query IdentityId --output text)
aws cognito-identity get-credentials-for-identity --identity-id "$IDENTITY_ID" \
  --logins "$LOGIN" --query Credentials
```

出力の `AccessKeyId`、`SecretKey`、`SessionToken` を環境変数 (`AWS_ACCESS_KEY_ID`、`AWS_SECRET_ACCESS_KEY`、`AWS_SESSION_TOKEN`) に入れて、次を実行します (別のシェルで行うと、元の認証情報に影響しません)。

| 試すこと | コマンド | 期待する結果 |
|:---|:---|:---|
| 利用者が自分の写真を置く | `aws s3api put-object --bucket "$BUCKET" --key "photos/${IDENTITY_ID}/test.jpg" --body <ファイル>` | 成功 |
| 利用者が他人の場所に置く | 上の `--key` の `${IDENTITY_ID}` を別の文字列にする | `AccessDenied` |
| 利用者が写真を読む | `aws s3api get-object --bucket "$BUCKET" --key "photos/${IDENTITY_ID}/test.jpg" /dev/null` | `AccessDenied` |
| 利用者がテーブルを読む | `aws dynamodb scan --table-name "$TABLE"` | `AccessDeniedException` |
| 管理者がテーブルを読む | 管理者でログインして、同じ `scan` | 成功 |
| 管理者が自分の写真を置く | 管理者でログインして、自分の ID の `put-object` | 成功 |

試したファイルはあとで、管理者の認証情報 (または自分の IAM ユーザー) で削除します。

## 10. 後片付け (リソースを消すとき)

```bash
aws s3 rm "s3://${BUCKET}" --recursive
aws s3api delete-bucket --bucket "$BUCKET"
aws dynamodb delete-table --table-name "$TABLE"
aws cognito-identity delete-identity-pool --identity-pool-id "$IDENTITY_POOL_ID"
aws cognito-idp delete-user-pool --user-pool-id "$USER_POOL_ID"
aws iam delete-role-policy --role-name gaia-neko-user --policy-name put-own
aws iam delete-role --role-name gaia-neko-user
aws iam delete-role-policy --role-name gaia-neko-admin --policy-name read-all
aws iam delete-role --role-name gaia-neko-admin
```

## 11. 料金の目安

- DynamoDB (オンデマンド)、S3、Cognito は少量なら、ほぼ無料です (Cognito のユーザープールは月に一定の数まで無料です)。
- 写真の保存量と、ダウンロードの量が増えると、S3 の料金が増えます。料金はリージョンと時期で、変わります。最新の料金表で、確認してください。
