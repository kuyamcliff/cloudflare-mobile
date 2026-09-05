# Cloudflare Mobile

A native Android (Kotlin + Jetpack Compose) client for managing Cloudflare zones from
your phone.

## What's in scope

Rather than a shallow wrapper around every Cloudflare product, this app covers core
account/zone management in full:

- **DNS Records** — list, add, edit, delete (A, AAAA, CNAME, MX, TXT, NS, CAA), proxy toggle
- **SSL/TLS** — encryption mode, Always Use HTTPS, minimum TLS version, Automatic HTTPS
  Rewrites, security level
- **Firewall** — firewall rules (expression + action) and IP access rules (block/challenge/allow)
- **Page Rules** — URL-based overrides (Always Use HTTPS, SSL mode, cache level), enable/disable, delete
- **Caching** — cache level, Development Mode, browser cache TTL, purge everything
- **Analytics** — requests, bandwidth, threats blocked, unique visitors (24h / 7d)
- **Accounts** — connect via a Cloudflare API token, manage multiple saved accounts, switch/remove

The architecture (Retrofit interface, repositories, ViewModels, Compose screens) is
deliberately modular so more Cloudflare products (Workers, R2, Zero Trust, Stream, ...) can
be added later without restructuring what's here.

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

## Building

Requires the Android SDK (compileSdk 34, minSdk 26) and JDK 17+.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest # unit tests (Retrofit/Moshi parsing, repositories, ViewModels)
./gradlew lintDebug
```

## Testing notes

Unit tests cover the networking layer (success/error/parsing via MockWebServer), every
repository, local token storage, and ViewModel state transitions (36 tests, JUnit +
MockWebServer + kotlinx-coroutines-test + Robolectric where Android APIs are needed).

There is no Android emulator or physical device in the environment this was built in, so
manual on-device UI testing has **not** been performed — install the APK on a real device
or emulator to verify the UI and interaction flows before relying on it.
