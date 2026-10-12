# Garmin OAuth 2 Migration Runbook

Last reviewed: 2026-10-11 against `OAuth2 Migration Guide_2.pdf`, `OAuth2PKCE_4.pdf`,
and `Garmin Developer Program_Start_Guide_1.2.1.pdf` in the repository root.

## Current Behavior

- **OAuth 1 remains the default.** Deployment does not exchange existing tokens or enable OAuth 2 signups.
- New OAuth 1 connections fetch `/partner-gateway/rest/user/id` using OAuth 1 credentials and save `garmin_userid` before completing the link. Existing users need the [backfill](garmin-oauth1-user-id-backfill.md).
- OAuth 2 signup uses authorization-code PKCE (S256), a random single-use state, and a ten-minute authorization session bound to the CubeTrek user. It is enabled only by `garmin.oauth2.signup-enabled=true`.
- Both signup paths check stable Garmin ID ownership. Relinking replaces stale credentials and migration state; it does not retain an unrelated old Garmin ID.
- OAuth 2 signup obtains the user ID and granted permissions. Activity ingestion is enabled only when `ACTIVITY_EXPORT` is granted.
- OAuth 2 access/refresh tokens are encrypted using the existing `thirdparty.clientkey.key` converter. Do not change that encryption key during migration.
- Refreshes re-read credentials under a database row lock and save the rotated token pair. Access tokens refresh with a ten-minute margin. Daily maintenance at 03:00 UTC refreshes connections whose refresh token expires within seven days, including permission-disabled users. Unknown refresh expiry is also checked daily. The cron is configurable with `garmin.oauth2.keep-alive-cron`.
- Expired refresh tokens or `invalid_grant` set a reconnect-required flag. Other errors retain credentials for retry. Reconnection must be initiated by the user; the profile's existing link action starts it. The current profile badge does not expose the new reconnect flag, so monitor it using the audit below.

## What the New PING Means

The migration guide explicitly requires using `userId` instead of `userAccessToken`
and using the supplied callback URL unchanged, including its additional token query
parameter. It says the new structure takes effect after the exchanged OAuth 1 token
expires. The old token remains valid for **30 days after that user's exchange**,
not 30 days after Garmin enables the application.

The receiver handles both formats. If a notification includes `userAccessToken`,
it uses OAuth 1 even when `userId` is also present. A user-ID-only activity notification
uses the stored OAuth 2 credentials and Bearer authentication. Numeric activity IDs
are accepted; missing IDs receive a local logging/filename identifier. The callback
URL is not reconstructed.

Activity download outcomes and retry logs now include `auth=OAuth1` or `auth=OAuth2`.
The label follows the notification's selected authentication path, not merely the
presence of stored OAuth 2 tokens. For example:

```text
Garmin Connect activity download successful: user=2, track=123456, auth=OAuth2
```

A successful `auth=OAuth2` activity import verifies the new notification/download
path. An error or ignored notification with that label only identifies the attempted
path; it is not a successful import. Credentials, authorization headers, and callback
URLs are not added to these logs. Deploy this logging change before using it to
observe the transition; older success lines without `auth` cannot identify the protocol.

The start guide specifies `deregistrations` and `userPermissionsChange` JSON examples.
Permission opt-out stops ingestion but retains valid credentials. Actual deregistration
clears both token sets. Incoming batches are validated before events are published.

These three PDFs **do not supply a complete Activity API `activityFiles` OAuth 2
payload**. Confirm the field names and callback host using Garmin's Activity API
specification or a real test notification. The permissions-endpoint example `{[...]}`
is invalid JSON. A live OAuth 2 pilot on 2026-10-11 confirmed that the endpoint returns
`{"permissions":["ACTIVITY_EXPORT"]}`. The implementation accepts this object wrapper
and the bare-array form, but rejects missing or malformed permission arrays.

Callback downloads only allow HTTPS on port 443 at `healthapi.garmin.com` and
`apis.garmin.com`, without redirects or embedded credentials. If Garmin uses another
host, verify it first and add its exact name to `garmin.callback.allowed-hosts`
(a comma-separated replacement list, not a wildcard). Do not log full callback URLs.

Transient download failures get up to three attempts. No-GPS/rejected files remain
normal ignored uploads. The webhook still acknowledges before asynchronous processing:
**there is no durable queue**, so a process crash or outage beyond those retries can
lose an acknowledged activity. Monitor failures and arrange timely recovery with
Garmin; the start guide guarantees activity-file availability for only 24 hours.

## Preparation While Still on OAuth 1

1. Back up `user_thirdparty_connect` and verify its existing encryption key is available.
2. Apply/review the additive schema in [garmin-oauth2-schema.sql](../scripts/garmin-oauth2-schema.sql), or let the normal application's existing Hibernate update policy add the columns during deployment. The migration CLI deliberately cannot create them.
3. Deploy with OAuth 2 signup disabled. Check an ordinary OAuth 1 signup and activity upload, including no-GPS files.
4. Run the user-ID backfill and audit **all** remaining OAuth 1 credentials, including users whose activity-export permission is disabled. Investigate failed/revoked credentials instead of silently counting them as ready.
5. Obtain an OAuth 2 Activity API sample and verify the callback host in a test environment. If no sample is available, limit live investigation to one explicitly authorized pilot account. Do not proceed to bulk migration until a real OAuth 2 notification/download and token refresh have been verified. The permission response contract was verified during the 2026-10-11 pilot.

Hibernate's existing physical naming produces `garmin_oauth2access_token` and
`garmin_oauth2exchange_started_at` (no underscore immediately after `2`). The SQL
and audit below intentionally use those names; do not rename existing token columns.

## Enable OAuth 2 With Garmin

Contact `connect-support@developer.garmin.com` with the consumer key after preparation.
Their enabling the app does not invalidate existing OAuth 1 users. Obtain the new
OAuth 2 secret from the developer portal and configure:

```properties
garmin.oauth2.client-id=YOUR_GARMIN_CLIENT_ID
garmin.oauth2.client-secret=YOUR_NEW_OAUTH2_SECRET
garmin.oauth2.signup-enabled=false
```

Retain `garmin.consumer.key` and `garmin.consumer.secret` for signed token exchange
and the overlap period. Keep all secrets in private configuration. Ensure
`cubetrek.address` produces the registered redirect URI:
`https://YOUR_HOST/profile/connectGarmin-step2`.

On a test deployment, enable OAuth 2 signup and verify consent accepted/denied,
ID lookup, permissions, relinking, forced access-token refresh, a real activity
callback, permission changes, and deregistration. Then enable
`garmin.oauth2.signup-enabled=true` in production for new links. That flag does not
migrate existing users; they continue with their existing credentials.

## Exchange Existing Users

Use **`garminMigrate`, not `bootRun`**. The isolated CLI imports only the required
services and repository, starts no web server or scheduled jobs, forces
`ddl-auto=validate`, and disables SQL/Flyway/Liquibase initialization. The old
`garmin.oauth2.migration.enabled` switch is no longer used.

Dry run (the default) lists candidate connection IDs without calling Garmin or
writing records. It still connects to the database and validates the schema:

```bash
./gradlew garminMigrate --args='--spring.profiles.active=production,secrets --spring.datasource.url=jdbc:postgresql://localhost:5433/cubetrek --garmin.oauth2.migration.dry-run=true'
```

Choose one connection ID from that output for an explicit pilot. The ID refers to
`user_thirdparty_connect.id`, not `users.id` or the Garmin user ID:

```bash
./gradlew garminMigrate --args='--spring.profiles.active=production,secrets --spring.datasource.url=jdbc:postgresql://localhost:5433/cubetrek --garmin.oauth2.migration.dry-run=false --garmin.oauth2.migration.connection-id=123'
```

After verification, omit `connection-id` and use
`--garmin.oauth2.migration.limit=10` for batches, or omit both for all candidates.
The command exits nonzero if any processed connection fails. Limits select by
connection ID; unresolved failures can occupy a limited batch on subsequent runs.

Candidates have OAuth 1 credentials and no migration-completed timestamp. **Having
a backfilled `garmin_userid` is required for a first exchange, not a reason to skip
it.** Permission-disabled connections are included. Successfully migrated rows
retain OAuth 1 credentials during the overlap window.

### Single-Account Refresh Check

After the pilot's token migration completes, the same isolated CLI can explicitly
refresh that one connection. It requires a positive `connection-id` and a completed
migration, never exchanges OAuth 1 tokens, and defaults to dry run. First check
readiness without changing credentials:

```bash
./gradlew garminMigrate --args='--spring.profiles.active=production,secrets --spring.datasource.url=jdbc:postgresql://localhost:5433/cubetrek --garmin.oauth2.migration.verify-refresh=true --garmin.oauth2.migration.connection-id=123 --garmin.oauth2.migration.dry-run=true'
```

With explicit approval for that account, change `dry-run` to `false`. This performs
one real refresh even if the access token is still valid, using the same database
row lock and encrypted persistence as activity downloads. It then re-reads the saved
credentials, checks refresh-token rotation, and queries Garmin's user ID and
permissions with the new access token. It does not alter ingestion permissions or
global signup settings. The standalone check can run locally against the configured
database; it does not require a web application redeployment.

Do not automatically repeat a failed refresh check. Tokens are committed before
identity/permission verification; failures in those later checks leave the new pair
saved. A failure during the refresh itself can have an uncertain outcome, just like
an exchange. Inspect the reported stage and saved state before deciding how to recover.
This check does not validate an activity notification or download. An ordinary upload
during the OAuth 1 overlap is a sync smoke test, not proof of OAuth 2 callback handling.

### Recovery States

Before sending the exchange, the CLI commits `garmin_oauth2exchange_started_at`.
It then saves the issued OAuth 2 token pair in a separate committed transaction
**before** querying user ID or permissions. Completion is recorded only after those
checks succeed.

- Completed timestamp present: skipped on later runs.
- OAuth 2 refresh token present, completion absent: rerun to resume verification using saved tokens; no second exchange.
- Started timestamp present, refresh token absent: outcome is uncertain (for example a timeout or database failure after Garmin issued tokens). Automatic re-exchange is refused. Review with Garmin or have the user reconnect via OAuth 2. Do not simply clear the marker or restore an old database backup and replay exchanges.
- Reconnect required: the saved refresh token has expired or been rejected. User reauthorization is necessary.
- ID mismatch/duplicate: stop and investigate account ownership. The backfilled ID is not overwritten and the row is not marked complete.

There is no atomic transaction spanning Garmin and PostgreSQL. Row locks prevent
ordinary concurrent local refreshes, but cannot recover a rotated token if the process
or database fails between Garmin issuing it and local persistence. These cases may
require reauthorization. Do not run concurrent migration pilots for the same connection.

Audit without displaying token values. The existing encryption converter stores
missing credentials as empty strings as well as NULL, so exclude both:

```sql
SELECT id, user_id, garmin_enabled,
       NULLIF(garmin_userid, '') IS NOT NULL AS has_user_id,
       garmin_oauth2exchange_started_at,
       garmin_oauth2migration_completed_at,
       NULLIF(garmin_oauth2refresh_token, '') IS NOT NULL AS has_oauth2_refresh_token,
       garmin_oauth2refresh_token_expires_at,
       garmin_oauth2reconnect_required
FROM user_thirdparty_connect
WHERE NULLIF(garmin_useraccesstoken, '') IS NOT NULL
   OR NULLIF(garmin_oauth2refresh_token, '') IS NOT NULL
ORDER BY id;
```

Do not roll back migrated credentials to pre-exchange tokens: Garmin may have rotated
or expired them. Keep dual-protocol handling during the transition. The migration guide
states OAuth 1 retires on **2026-12-31**; plan to finish and validate before that date.

## Verification and Remaining Work

Offline checks, without loading the production Spring context:

```bash
./gradlew test --tests 'com.cubetrek.upload.garminconnect.*'
python3 -m unittest discover -s scripts -p 'test_*.py'
```

The tests cover PKCE/state, OAuth 1 ID persistence, migration recovery, token rotation,
notification parsing, callback validation, retries, and backfill update guards. They
mock Garmin and database access, so they do not prove PostgreSQL lock behavior or the
full live Garmin contract. The 2026-10-11 pilot verified token exchange, encrypted
token persistence, Bearer user-ID lookup, and the permission response shape. A live
single-account refresh check also verified the new client credentials, rotated-token
persistence, and identity/permissions using the refreshed access token. The user
confirmed an automatic activity import after deployment; its success log does not
identify the authentication protocol, so a real OAuth 2 activity callback/download
still needs verification. Only one connection has been migrated. Validate
concurrency behavior in an isolated staging environment. The ordinary
full test suite contains an application-context test: do not run it against production
configuration with Hibernate schema updates enabled.

Still separate rollout work: remaining live Garmin validation and production exchanges,
operator monitoring, and durable download recovery. If a user-facing account deletion
or Garmin disconnect mechanism is added, call Garmin's `DELETE /user/registration`
before claiming consent is removed; receiving a deregistration webhook is not a
substitute for that outbound call.
