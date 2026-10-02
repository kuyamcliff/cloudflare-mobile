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
gzipped compact registry (about 400 KB for 3,636 operations) with, per operation: method,
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

Jetpack Compose with Material 3 and a small design system in `ui/design`: graphite ink for
text, primary buttons and selected controls, Cloudflare orange only as an accent (the command
bar, focus, the selected tab, progress). Light and dark schemes define every surface-container
role, so existing screens built on stock Material components pick up the look without
per-screen changes. `Group`, `ListRow`, `IconTile`, `Pill`, `Tag`, `Banner` and
`PrimaryButton` are the building blocks; lists render as rounded card groups with inset
hairlines.

`MainShell` has three tabs, Home, Browse and Activity, an app bar that names the working
account (tap to switch) and an avatar that opens the profile screen (profiles, token access,
API tokens, tools, app lock, settings, diagnostics). Above the tabs, on every destination,
sits the command bar: "What do you want to do?".

- **Home**: zones in the working account with status and plan (favorites first), pinned
  actions, product shortcuts the token can use, recent actions.
- **Browse**: every product for the working zone or the account, grouped by purpose, plus
  the operation catalog, GraphQL analytics and the raw API explorer.
- **Activity**: local changes and requests from this device, transfers and Cloudflare's audit
  log.
- **Zone hub**: status, nameservers, live switches for development mode, Under Attack mode
  and Always Use HTTPS, purge everything (disabled with a reason when the token can't purge),
  and every zone feature grouped by purpose.

A status tag appears in the app bar only when something is wrong: offline, rate limited, or
repeated server errors. A banner appears when Cloudflare rejects the token.

## Commands and actions (`core/command`, `ui/command`, `ui/action`)

```
typed text ──> CommandEngine ──┬─ recipes (64 common tasks, slot filling)
                               ├─ places (native screens, in the named zone)
                               ├─ resources (zones, Workers, buckets, KV, D1, Pages, queues, tunnels)
                               └─ operations (all 3,636 in the registry)
                    │
                    └── nothing explains it ──> AiPlanner (Workers AI, on request)
                                                        │
                                   ActionDraft <────────┘
                                        │
                                   ActionScreen: review form ─> confirm ─> run ─> results ─> follow-ups
```

- `CommandText` tokenizes, maps synonyms ("add" is create, "clear" is purge, "site" is zone),
  and extracts quoted strings and `name=value` assignments.
- `CommandEngine` scores recipes by phrase coverage and by how much of the text their slots
  explain, places by name, resources by name, and operations by summary, path and group with
  the verb mapped to a method. It recognizes zones named in the text ("on example.com",
  "www.example.com"), fills `zone_id` and `account_id`, and flags anything the token's
  policies rule out so it is never the default. It is pure and unit tested against the real
  registry.
- `Recipe` declares an operation plus slots: IPs, emails, URLs, hostnames, countries, numbers,
  quoted text, enum words, on/off, flags and free words, written to path, query or (dotted)
  body fields. Every recipe's method and path is checked against the registry in tests.
- `AiPlanner` asks Workers AI to pick product groups from the whole schema, then one
  operation among those groups plus the best local matches, with path, query and body values.
  The answer is validated against the chosen operation and becomes a draft; it never runs.
- `ActionScreen` renders any operation from the registry: path placeholders with pickers
  loaded from the list operation one level up (DNS records for `{dns_record_id}`, buckets for
  `{bucket_name}`), query parameters, and a typed body form generated from the schema (enums
  as chips or menus, booleans as switches, numbers, comma lists, JSON for objects) with a JSON
  mode. Reads run directly; changes show the exact request in a confirmation sheet first.
  Results render as a list of items or a key/value view, page with "load more", and offer
  follow-ups: the item operations under a list (details, edit prefilled with current values,
  delete) and sibling operations of an item.
- `ActionStore` hands drafts to the action screen and keeps per-profile recent and pinned
  actions without bodies. `ResourceIndex` caches the account's resource names for five
  minutes.

The registry generator emits typed top-level body fields per operation (name, type,
required, enum values, description, default or example, array item type), unioning
`oneOf`/`anyOf` variants so, for example, every DNS record type is offered.

Every destructive action in native screens goes through `DestructiveConfirmDialog`: title
names the resource, the body states the consequence and target context (profile, account,
zone), and critical actions (roll or revoke a token, wipe local data) require typing a
confirmation.

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
SharedPreferences: settings, working context, capability cache (permission metadata only),
recent and pinned actions (no request bodies).
EncryptedSharedPreferences: API token secrets and R2 S3 secrets. Nothing is backed up.
