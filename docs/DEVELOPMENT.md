# Development

## Layout

```
app/src/main/java/dev/cfmobile/app/
  AppContainer.kt           service locator, profile tracking, local data actions
  core/api/                 endpoint registry, raw API client, cURL codec
  core/capabilities/        capability engine, schema probes, feature registry
  core/errors/              error classification and user-facing copy
  core/net/                 connectivity monitor
  core/security/            app lock, settings, secure clipboard
  core/tokens/              token policy builder, risk observations, templates
  core/transfers/           SigV4, R2 S3 client, transfer repository and worker
  data/local/               credential and metadata stores, context store, Room
  data/remote/              Retrofit API, interceptors, network status, DTOs
  data/repository/          one repository per product area
  ui/                       Compose screens, one package per feature
tools/openapi/              registry generator
tools/ci/                   secret scan
```

## Regenerating the endpoint registry

```bash
git clone --depth 1 https://github.com/cloudflare/api-schemas.git /tmp/api-schemas
python3 tools/openapi/generate_registry.py \
  --schema /tmp/api-schemas/openapi.json \
  --revision "$(git -C /tmp/api-schemas rev-parse HEAD)" \
  --previous app/src/main/assets/cf_endpoints.bin
```

This rewrites the asset and `docs/API_COVERAGE.md`. Besides methods, paths, parameters and
permissions, each operation carries typed top-level body fields, which the action screen turns
into forms. Adding a Retrofit endpoint that matches a
schema operation marks it Native on the next run.

## Adding a native screen

1. Add the Retrofit call, repository, view model and screen.
2. Add a `Capability` row to `CapabilityRegistry` with its route.
3. Bind it to a read operation in `CapabilityProbes`.
4. Add tests with `MockWebServer` (see `data/remote/TestApiFactory.kt`).

## Tests

- JVM tests with MockWebServer for every repository, the network stack and the transfer client.
- Robolectric tests where Android APIs are needed (stores, deep links, file streaming).
- Robolectric Compose tests that render real screens with real view models.

Test API clients disable retries so each test sees exactly the response it enqueued; the retry
policy has its own tests. The default test run never uses a real token.

### Design screenshots

`DesignScreensTest` renders the shell, command surface, action form and results, zone hub,
profile, login and a native screen with real view models against a fake Cloudflare, and checks
what each must show. To review the design, write PNGs in light and dark:

```bash
CF_SCREENSHOTS_DIR=/tmp/shots ./gradlew testDebugUnitTest --tests '*DesignScreensTest'
```

### Live end-to-end tests

`e2e/LiveCloudflareE2ETest` runs against the real API through the app's own network stack,
repositories, command engine and action view model. It is skipped unless a token is in the
environment; never put one in a file:

```bash
CF_E2E_TOKEN=... ./gradlew testDebugUnitTest --tests 'dev.cfmobile.app.e2e.*'
```

It verifies the token and reads its policies, calls every native repository read and fails on
any response a DTO can't parse, calls every account and zone GET in the schema and opens the
first item of each list, creates, uses and deletes throwaway resources by typed command
(KV with values, D1 with SQL, R2, a queue, a tunnel, an IP list, a Turnstile widget, an Access
service token, and a DNS TXT record that is also edited through a result follow-up), re-saves
zone settings with their current values, runs Workers AI prompts and plans, and resolves
commands against live names. Everything it creates is named `cfctl-e2e-*`; leftovers from an
interrupted run are removed first. It never changes existing DNS records, settings or
resources. A Markdown report is written to `app/build/e2e/report.md`; see `E2E.md` for the
latest results.
