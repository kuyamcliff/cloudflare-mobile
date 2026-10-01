# Cloudflare Control

A native Android client for the Cloudflare API. Connect a Cloudflare API token, then type what
you want to do. Anything the token allows, across every product in Cloudflare's API, is a
sentence or a search away, and nothing runs until you have reviewed it.

Cloudflare Control is an independent project. It is not made or endorsed by Cloudflare, Inc.

## Principles

- **Cloudflare controls authorization; the app reflects it.** After a token is connected, the
  app reads the token's own policies when Cloudflare allows it and learns from real responses
  otherwise. Features the token cannot use are hidden, not shown broken.
- **Your token never leaves the device except to Cloudflare.** There is no backend, no
  account with this app, no analytics and no tracking SDK. Tokens are encrypted with a key held
  in the Android Keystore and are attached only to requests for `api.cloudflare.com`.
- **Say it or find it.** One command bar on every screen understands tasks ("add A record www
  192.0.2.1 on example.com", "turn off dev mode", "block traffic from russia"), screens, the
  names of your own resources, and all 3,636 operations in Cloudflare's published schema. Each
  becomes a prefilled form you review; changes show the exact request before they are sent.
- **Native screens where they matter, the whole API everywhere else.** Every operation without
  a dedicated screen gets a generated form with pickers for its targets, typed fields for its
  body, readable results and follow-up actions on each result.

## What it does

| Area | Highlights |
| --- | --- |
| Command bar | Recipes for common tasks with slot filling, screens, your resources, every API operation; `name=value` for any field; optional planning with Workers AI in your own account |
| Actions | Any operation as a form: target pickers, typed body fields or JSON, review and confirm, list and detail results, load more, edit or delete any result, pin to Home |
| Profiles | Multiple tokens, per-profile accounts and zones, fingerprints, biometric app lock |
| Home | Zones with status, pinned and recent actions, product shortcuts the token can use |
| Zone hub | Status and nameservers, live switches for development mode, Under Attack and Always HTTPS, purge, every zone feature by purpose |
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
CF_E2E_TOKEN=... ./gradlew testDebugUnitTest --tests 'dev.cfmobile.app.e2e.*'   # live, optional
```

See `docs/DEVELOPMENT.md` for the project layout and `docs/RELEASE.md` for signing.

## Documentation

- `docs/ARCHITECTURE.md`: token flow, capability engine, API layer, transfers, caching
- `docs/SECURITY.md`: what is stored, where, and what never leaves the device
- `docs/PERMISSIONS.md`: how permissions are discovered and evaluated
- `docs/API_COVERAGE.md`: generated coverage matrix
- `docs/E2E.md`: results of the live end-to-end suite
- `docs/DEVELOPMENT.md`, `docs/RELEASE.md`, `docs/TROUBLESHOOTING.md`

## License

MIT, see `LICENSE`.
