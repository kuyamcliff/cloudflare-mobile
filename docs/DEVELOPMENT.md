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

This rewrites the asset and `docs/API_COVERAGE.md`. Adding a Retrofit endpoint that matches a
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
policy has its own tests. No real tokens are ever used in tests.
