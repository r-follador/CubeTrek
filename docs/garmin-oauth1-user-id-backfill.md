# Garmin OAuth 1 User-ID Backfill

Last reviewed: 2026-10-11. Run this **before** exchanging existing users' OAuth 1 tokens.

`scripts/backfill_garmin_user_ids.py` queries
`GET https://apis.garmin.com/partner-gateway/rest/user/id` using each user's existing
OAuth 1 credentials and saves the result to `user_thirdparty_connect.garmin_userid`.
This does not exchange tokens or switch the app to OAuth 2. The endpoint was already
successfully used with this installation's OAuth 1 credentials before migration.

New OAuth 1 signups now fetch and save the same ID automatically. Existing records
still need a backfill. The script includes connections with nonempty OAuth 1 token
and token-secret fields and a missing/empty Garmin ID, **even if `garmin_enabled`
is false**: permission opt-out does not necessarily revoke the token. Older disabled
records may instead be revoked; investigate failures rather than forcing an ID.

## Setup

The script uses `psycopg` and `cryptography`, including the application's existing
SHA-256/AES decryption scheme. Use an isolated environment, not system-wide pip:

```bash
python3 -m venv .venv-garmin-user-id-backfill
.venv-garmin-user-id-backfill/bin/python -m pip install 'psycopg[binary]' cryptography
```

On Debian/Ubuntu, install the matching `python3-venv` package if environment creation
fails. If the environment exists but reports `No module named pip`, use an available
`uv` installation to install directly into it:

```bash
uv pip install --python .venv-garmin-user-id-backfill/bin/python 'psycopg[binary]' cryptography
```

Do not use `--break-system-packages`. Back up the connection table and ensure the
nullable unique `garmin_userid` column exists before applying updates. See the
[additive schema](../scripts/garmin-oauth2-schema.sql).

## Configuration

By default the script reads `src/main/resources/application-secrets.properties`,
using `spring.datasource.url.production`, `spring.datasource.username.production`,
and `spring.datasource.password.production`, falling back to their unsuffixed keys.
It also reads `thirdparty.clientkey.key`, `garmin.consumer.key`, and
`garmin.consumer.secret` without printing values.

For another layout pass `--properties-file /path/to/application-secrets.properties`;
use `--profile dev` for `.dev` datasource keys. This is a simple properties reader,
not a Spring runtime: it does not resolve `${...}` placeholders, imports, or all Java
properties escape syntax. Use literal values or environment overrides.

Environment overrides: `CUBETREK_DATABASE_URL`, `CUBETREK_DATABASE_USER`,
`CUBETREK_DATABASE_PASSWORD`, `CUBETREK_THIRDPARTY_CLIENTKEY`,
`GARMIN_CONSUMER_KEY`, and `GARMIN_CONSUMER_SECRET`.

`--database-url` takes precedence over both environment and file values. It accepts
JDBC PostgreSQL URLs and removes the `jdbc:` prefix. This installation's override is
`jdbc:postgresql://localhost:5433/cubetrek`; username/password still come from the
selected properties or environment. JDBC-specific connection options are not translated.

## Run

One-user dry run (calls Garmin, but does not update the database):

```bash
.venv-garmin-user-id-backfill/bin/python scripts/backfill_garmin_user_ids.py \
  --database-url jdbc:postgresql://localhost:5433/cubetrek \
  --limit 1
```

Small applied batch:

```bash
.venv-garmin-user-id-backfill/bin/python scripts/backfill_garmin_user_ids.py \
  --database-url jdbc:postgresql://localhost:5433/cubetrek \
  --apply --limit 10
```

Then omit `--limit` to process remaining eligible rows. Each successful update is
committed immediately. Existing IDs are not overwritten. Updates also require that
the OAuth 1 credentials have not changed during the request, preventing a concurrent
relink from receiving the old account's ID.

The script exits nonzero if a row fails. Failed rows remain unchanged. Revoked
credentials, duplicate ownership, and configuration problems need review; repeated
limited runs may select the same failures until resolved.

Audit coverage without displaying credentials:

```sql
SELECT id, user_id, garmin_enabled,
       NULLIF(garmin_userid, '') IS NOT NULL AS has_user_id,
       NULLIF(garmin_useraccesstoken_secret, '') IS NOT NULL AS has_token_secret
FROM user_thirdparty_connect
WHERE NULLIF(garmin_useraccesstoken, '') IS NOT NULL
  AND (NULLIF(garmin_userid, '') IS NULL
       OR NULLIF(garmin_useraccesstoken_secret, '') IS NULL)
ORDER BY id;
```

Review all returned rows before proceeding to the [OAuth 2 migration](garmin-oauth2-migration.md).
Populating `garmin_userid` alone does not prepare an account for Bearer downloads;
OAuth 2 tokens must also be obtained and maintained.
