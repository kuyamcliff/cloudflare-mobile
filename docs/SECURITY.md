# Security

## What is stored, and where

| Data | Storage | Protection |
| --- | --- | --- |
| Cloudflare API token secrets | `cf_credentials` EncryptedSharedPreferences | AES-256-GCM values, key in Android Keystore (hardware-backed where available) |
| R2 S3 access key and secret | `cf_r2_credentials` EncryptedSharedPreferences | Same as above, separate file |
| Profile metadata: label, token ID, fingerprint, expiry | SharedPreferences | Not secret. The fingerprint is the first 12 hex chars of SHA-256(secret) |
| Working account and zone, favorites | SharedPreferences | Not secret |
| Capability cache: token policies (permission names and resource IDs) | SharedPreferences | Not secret, no token value |
| Request history: method, path, status, duration | Room | No headers, no bodies, credential-like query values redacted |
| Transfers and saved request templates | Room | No credentials |
| Recent and pinned actions | SharedPreferences (`cf_actions`) | Method, path template, path and query values, title. Request bodies are never stored, since they can carry secrets |

Backups and device-to-device transfer exclude every domain (`data_extraction_rules.xml`,
`allowBackup=false`).

## What never leaves the device

Token secrets and R2 secrets are only ever sent to Cloudflare:

- The API token is attached by `AuthInterceptor` only when the request's scheme, host and port
  equal `https://api.cloudflare.com`. For any other host the Authorization header is removed,
  even if a caller set one. Redirects are disabled.
- R2 S3 credentials are used only by `R2S3Client`, which refuses any host that is not
  `<account>.r2.cloudflarestorage.com` (optionally with a jurisdiction label), and never carries
  the API token.
- The API Explorer builds URLs only from relative paths under the API base, rejects absolute
  URLs, scheme-relative paths and `..` segments, and refuses user-set `Authorization`, `Cookie`,
  `Host`, `X-Auth-*` and `Proxy-*` headers. cURL import strips credentials and only accepts
  `api.cloudflare.com`; cURL export prints `Bearer <REDACTED>`.

There is no application backend, no analytics SDK, no crash reporter and no advertising SDK.

## Typed commands and Workers AI

Everything typed into the command bar is matched on the device: recipes, screens, the user's
own resource names and the bundled operation registry. Nothing typed is sent anywhere while
matching.

Planning with Workers AI happens only when the user asks for it (the "Plan with Workers AI"
row, or pressing enter when nothing local explains the request). It is a normal API call to
`POST /accounts/{account_id}/ai/run/@cf/meta/llama-3.3-70b-instruct-fp8-fast` on the user's own
account, through the same host-bound client as every other call, so the token still goes only
to `api.cloudflare.com`. The prompt contains the typed text, the working zone and account IDs,
up to 30 zone names with their IDs, and the descriptions of candidate operations. The model's
answer only ever becomes a draft: the user reviews every field in the action form and nothing
is sent until they press run and, for changes, confirm.

Every action, typed or planned, goes through the same rails as the native screens: local
validation, a confirmation sheet showing the exact method, path, target and body for anything
that is not a read, and a typed confirmation for deleting zones, buckets, databases,
namespaces, Workers, Pages projects, tunnels, members and tokens.

## Logs

Release builds do not log HTTP traffic. Debug builds log one line per request, with identifier
segments replaced by `{id}` and no query string, headers or bodies. R8 strips `Log.d` and
`Log.v` calls from release builds. Exceptions are reported to the UI by message only where the
message comes from Cloudflare or the network stack, never containing request data.

## Screens and clipboard

- Screens showing a secret (new or rolled token, R2 credentials, the connect screen) set
  `FLAG_SECURE`, which blocks screenshots and blanks the recents preview. A global setting
  applies it to the whole app.
- Copying a secret marks the clip sensitive (hidden from clipboard previews on Android 13+) and
  clears it after a configurable delay unless something else was copied since.
- Secrets are shown masked until revealed, and a token secret is shown only once, as Cloudflare
  returns it. The view model drops it when the screen is left.

## App lock

App lock uses `BiometricPrompt` with device credential fallback; the app does not implement
its own password scheme. The lock engages after a configurable time in the background.

## Destructive actions

All deletions and credential changes go through one confirmation component that names the
resource, the consequence and the target profile, account and zone. Rolling or revoking a token
and wiping local data require typing a confirmation. Removing a profile from the device and
revoking a token in Cloudflare are separate actions with separate wording.

## Deep links

`cloudflarecontrol://` links only navigate. They never carry or accept credentials, never
switch profiles, and every identifier is validated against Cloudflare's 32-hex format.

## TLS

The platform trust store is used with standard hostname verification. Cleartext traffic is
disabled. Certificates are not pinned, because Cloudflare does not document stable pins for
its API and a stale pin would lock users out.

## Threat model summary

| Threat | Mitigation |
| --- | --- |
| Lost or stolen device | Device lock plus optional biometric app lock; Keystore-bound encryption |
| App data extracted | Secrets are encrypted with a non-exportable Keystore key |
| Logs or diagnostics shared | No secrets in logs; the diagnostics export contains metadata only |
| Malicious host via explorer, cURL or deep link | Host-bound token injection, path validation, link validation |
| Network attacker | TLS with platform trust, no cleartext, no redirects |
| Tampered local database | Contains no secrets; credentials are authenticated-encrypted |

## Reporting

Please report vulnerabilities privately to the maintainers rather than in a public issue.
