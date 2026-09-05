# Cloudflare Mobile

A native Android (Kotlin + Jetpack Compose) client for managing Cloudflare zones from
your phone.

## What's in scope

Rather than a shallow wrapper around every Cloudflare product, this app covers core
account/zone management in full:

- **DNS Records** — list, add, edit, delete; A, AAAA, CNAME, MX, TXT, NS, PTR, SPF, CAA,
  plus structured-data record types SRV, URI, TLSA, NAPTR, SSHFP, CERT; proxy toggle;
  record detail view; batch delete; BIND zone file import/export
- **SSL/TLS** — encryption mode, Always Use HTTPS, minimum TLS version, Automatic HTTPS
  Rewrites, security level
- **Firewall** — legacy firewall rules and IP access rules (block/challenge/allow), plus
  the modern **WAF Custom Rules** and **Rate Limiting** engines (Cloudflare Rulesets)
- **Transform Rules** — URL Rewrite and request/response header modification
- **Page Rules** — URL-based overrides (Always Use HTTPS, SSL mode, cache level), enable/disable, delete
- **Caching** — cache level, Development Mode, browser cache TTL, purge everything
- **Analytics** — requests, bandwidth, threats blocked, unique visitors (24h / 7d)
- **Account Members** — list, invite, and remove account members with roles
- **Accounts** — connect via a Cloudflare API token, manage multiple saved accounts,
  switch/remove, app lock with biometric/device-credential authentication

The architecture (Retrofit interface, repositories, ViewModels, Compose screens, a
capability registry driving navigation instead of hard-coded routes) is deliberately
modular so more Cloudflare products (Workers, R2, Zero Trust, Audit Logs, ...) can be
added later without restructuring what's here — see `CapabilityRegistry.kt` for exactly
what's implemented vs. still on the roadmap, including honest notes on scope gaps (e.g.
WAF Custom Rules vs. Managed Rulesets) and which request formats have and haven't been
verified against a live API call.

## How auth works

You connect with a Cloudflare **API Token** (Cloudflare dashboard → Profile → API Tokens →
Create Token), never your account password. The token is:

- Verified against Cloudflare's API before it's ever saved
- Stored only in `EncryptedSharedPreferences`, encrypted at rest with a key held in the
  Android Keystore
- Excluded from Android's auto backup / cloud backup and device-transfer (see
  `app/src/main/res/xml/data_extraction_rules.xml`)
- Never sent anywhere except as an `Authorization` header on requests to
  `api.cloudflare.com` — there is no backend server for this app

Non-secret zone data (the Zones list only, for now) is cached locally in a Room database
so it displays instantly on next launch instead of a blank loading screen, with a
"Updated Xs ago" label disclosing how stale it is. The cache is never a source of truth
and holds no secrets - losing it just costs a network round trip.

## Building

Requires the Android SDK (compileSdk 37, minSdk 26) and JDK 17+ (developed against JDK 21).

```bash
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # unsigned release APK (see "Signing" below)
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug
```

## Continuous integration

`.github/workflows/android-ci.yml` runs `testDebugUnitTest`, `assembleDebug`, and
`lintDebug` on every push and pull request. It does **not** run macrobenchmarks or
generate a real Baseline Profile - both require executing instrumented tests on a
physical device or emulator, which this project's development environment (and this CI
job) doesn't have. Treat that as an open gap, not a silent omission: add a
`benchmark` module and a device/emulator-backed CI job (e.g. via `reactivecircus/android-emulator-runner`)
before relying on either.

## Signing

Release builds (`assembleRelease` / `bundleRelease`) currently produce an **unsigned**
artifact - there is no release signing key configured, and one was deliberately not
generated on this project's behalf (that's a decision for whoever owns the Play Store
listing). Configure a real signing config in `app/build.gradle.kts` before distributing
a release build anywhere.

## Testing notes

Unit tests cover the networking layer (success/error/parsing via MockWebServer), every
repository, local token storage, Room caching, and ViewModel state transitions (176
tests, JUnit + MockWebServer + kotlinx-coroutines-test + Robolectric where Android APIs
are needed).

There is no Android emulator or physical device in the environment this was built in, so
manual on-device UI testing has **not** been performed — install the APK on a real device
or emulator to verify the UI and interaction flows before relying on it.
