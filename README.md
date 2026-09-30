# Cloudflare Control

A native Android client for the Cloudflare API. Connect a Cloudflare API token and manage the
accounts, zones and products that token can reach, directly from your phone.

Cloudflare Control is an independent project. It is not made or endorsed by Cloudflare, Inc.

## Principles

- **Cloudflare controls authorization; the app reflects it.** After a token is connected, the
  app reads the token's own policies when Cloudflare allows it and learns from real responses
  otherwise. Features the token cannot use are hidden, not shown broken.
- **Your token never leaves the device except to Cloudflare.** There is no backend, no
  account with this app, no analytics and no tracking SDK. Tokens are encrypted with a key held
  in the Android Keystore and are attached only to requests for `api.cloudflare.com`.
- **Native screens where they matter, the whole API everywhere else.** Every one of the 3,627
  operations in Cloudflare's published OpenAPI schema is reachable through the API Explorer,
  with the same safety rails as native screens.

## What it does

| Area | Highlights |
| --- | --- |
| Profiles | Multiple tokens, per-profile accounts and zones, fingerprints, biometric app lock |
| Home | Working account and zone, permission-aware quick actions, favorites and recents |
| Zones | DNS (typed forms, import/export, bulk delete), SSL/TLS, certificates, caching, rules, WAF, rate limiting, page rules, analytics, and 30 more zone features |
| Developer platform | Workers, routes, domains, secrets, KV, D1 console, Queues, Durable Objects, Workflows, Hyperdrive, Vectorize, Workers AI, Pages |
| R2 | Buckets, folder browsing, prefix search, previews, bulk delete, uploads and downloads |
| Transfers | REST uploads up to 300 MB, resumable parallel S3 multipart beyond that, MD5 verified, background with notifications |
| Zero Trust | Access apps, policies, groups, identity providers, service tokens, Gateway, tunnels, device posture, networks |
| Tokens | Create (live permission catalog, templates, resource scope, IP and TTL conditions), edit, roll, revoke, local observations |
| Analytics | GraphQL console with bounded dataset templates, table and JSON views, saved queries |
| Everything else | All Cloudflare APIs catalog, API Explorer, cURL import/export, request history |

`docs/API_COVERAGE.md` lists, per product group, how many operations have a native screen and
how many are available through the generic API layer. It is generated, not hand-maintained.

## Build

Requires JDK 17+ (built with JDK 21) and the Android SDK (compileSdk 37, minSdk 26).

```bash
./gradlew testDebugUnitTest   # unit, integration and Robolectric Compose UI tests
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease     # signed when keystore.properties or CF_RELEASE_* env vars exist
```

See `docs/DEVELOPMENT.md` for the project layout and `docs/RELEASE.md` for signing.

## Documentation

- `docs/ARCHITECTURE.md`: token flow, capability engine, API layer, transfers, caching
- `docs/SECURITY.md`: what is stored, where, and what never leaves the device
- `docs/PERMISSIONS.md`: how permissions are discovered and evaluated
- `docs/API_COVERAGE.md`: generated coverage matrix
- `docs/DEVELOPMENT.md`, `docs/RELEASE.md`, `docs/TROUBLESHOOTING.md`

## License

MIT, see `LICENSE`.
