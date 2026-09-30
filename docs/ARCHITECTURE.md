# Architecture

```
                     Cloudflare Control
                            |
          +-----------------+-----------------+
          |                 |                 |
       REST API         GraphQL API        R2 / S3
          |                 |                 |
          +-----------------+-----------------+
                            |
                    Capability Engine
                            |
             +--------------+--------------+
             |                             |
        Native screens               Generic API layer
             |                             |
             +--------------+--------------+
                            |
                      Compose UI shell
```

The app is a single Gradle module organized by layer (`core`, `data`, `ui`). A hand-rolled
service locator (`AppContainer`) builds every dependency once; view models receive their
dependencies through constructors, which keeps them testable without a DI framework.

## Token flow

1. The user pastes a token on the connect screen.
2. `AuthRepository` verifies it with `GET /user/tokens/verify` using an explicit header, before
   anything is written. Account-owned tokens fall back to a lightweight zones call.
3. Only after verification the secret is written to `CredentialStore` (Keystore-backed
   AES-256-GCM). Non-secret metadata (label, token ID, fingerprint, expiry) goes to
   `AccountMetadataStore`. The pasted value is dropped from UI state.
4. `CapabilityRepository.discover()` runs (see Capability engine). The onboarding screen shows
   each real step.
5. `AccountStore.activeIdFlow` emits; the container reloads the working context, resets
   network status, and rediscovers capabilities for the new profile.

## Network layer (`data/remote`)

One OkHttp client with a fixed interceptor order:

1. `RequestHistoryInterceptor`: one sanitized row per logical request (method, path, status,
   duration). No headers, no bodies, credential-like query values redacted.
2. `RetryPolicyInterceptor`: honors `Retry-After` on 429 for any method, retries 502/503/504 and
   connection failures only for idempotent methods, never retries 4xx. Waits are cancellable.
3. `AuthInterceptor`: attaches the active token only when scheme, host and port match the API
   base. Any other host gets the Authorization header stripped.
4. `RedactingLogInterceptor`: debug builds only, logs `GET /zones/{id}/dns_records status=200`.

Redirects are disabled. `safeApiCall` converts responses into `ApiResult` and never swallows
coroutine cancellation. The API version lives only in `NetworkModule.API_VERSION_PATH`.

## Endpoint registry and generic API layer (`core/api`)

`tools/openapi/generate_registry.py` reads Cloudflare's official schema
(`github.com/cloudflare/api-schemas`) and produces `app/src/main/assets/cf_endpoints.bin`, a
gzipped compact registry (about 300 KB for 3,627 operations) with, per operation: method,
path, summary, product group, accepted permissions (`x-api-token-group`), plan availability,
deprecation, path and query parameters, and a JSON body example. The generator also scans the
Retrofit interface to mark which operations have a native screen, and writes
`docs/API_COVERAGE.md`.

`EndpointRegistry` streams that asset into memory once, off the main thread. It powers:

- **All Cloudflare APIs**: grouped by product, scope, method or permission, filtered by what the
  token can use.
- **API Explorer**: path parameters auto-filled from the working account and zone, known query
  parameters offered with types and enums, body examples, validation before sending, mutation
  confirmation with target context, response viewer, save full response via the Storage Access
  Framework, templates, cURL import/export.
- Labels in the activity timeline ("Create DNS Record" rather than a raw path).

`RawApiClient` executes explorer requests through the same OkHttp stack and adds its own
boundary: only relative paths under the API base, no `..` segments, no user-set credential or
routing headers.

## Capability engine (`core/capabilities`)

`CapabilityRepository` builds a `TokenCapabilities` graph per profile:

- **Identity**: user token via `/user/tokens/verify`, else account token via
  `/accounts/{id}/tokens/verify`.
- **Policies**: `GET /user/tokens/{id}` or `/accounts/{id}/tokens/{id}`. When readable, every
  allow and deny grant is flattened with its resource scope (all accounts, specific accounts,
  all zones, zones in an account, specific zones, user).
- **Observations**: every 2xx and 403 response is matched to its schema operation and recorded.
  A 403 schedules at most one rediscovery per minute.

Each native screen is bound to one representative read operation in `CapabilityProbes`; the
permissions it needs come from the schema, not from a hardcoded list. States are
`AVAILABLE_READ`, `AVAILABLE_WRITE`, `TOKEN_RESTRICTED`, `UNKNOWN` (and plan/API states for
future use). Discovery only ever makes GET requests. See `PERMISSIONS.md`.

## UI

Jetpack Compose with Material 3 and a warm-neutral palette, orange as the accent only.
`MainShell` hosts four tabs:

- **Home**: working account and zone, quick actions filtered by capability, favorite and recent
  zones, token expiry warning.
- **Resources**: zone and account features the token can use, with read-only badges and a
  count of hidden features that can be revealed.
- **Activity**: local changes and all requests from this device (repeatable in the explorer),
  links to transfers and to Cloudflare's server-side audit log, which is never mixed in.
- **More**: token access, API tokens, tools, security, settings, diagnostics.

A status badge appears in the app bar only when something is wrong: offline, rate limited, or
repeated server errors. A banner appears when Cloudflare rejects the token.

Every destructive action goes through `DestructiveConfirmDialog`: title names the resource,
the body states the consequence and target context (profile, account, zone), and critical
actions (roll or revoke a token, wipe local data) require typing a confirmation.

## R2 and transfers (`core/transfers`)

- Listing, delete and small reads use the R2 REST API (`/accounts/{id}/r2/buckets/{b}/objects`)
  with server-side prefix search, delimiter folders and cursor pagination.
- Uploads up to Cloudflare's documented 300 MB REST limit stream through `FileRangeRequestBody`.
- With R2 S3 credentials (stored separately, Keystore encrypted), large uploads use S3 multipart:
  8 MiB or larger parts sized to stay under 10,000 parts, parallelism from settings, each part's
  MD5 compared with R2's ETag, completed parts persisted so a job resumes after process death.
- Downloads stream to a user-chosen document; with S3 credentials they resume with a ranged GET
  from the bytes actually on disk.
- `TransferWorker` runs under WorkManager as a `dataSync` foreground service with real progress,
  exponential backoff for retryable failures and none for permanent ones. Every job captures its
  profile, account, bucket and key when created.

## Local data

Room (`CfDatabase`): zone cache, request history, saved requests, transfers and multipart parts.
SharedPreferences: settings, working context, capability cache (permission metadata only).
EncryptedSharedPreferences: API token secrets and R2 S3 secrets. Nothing is backed up.
