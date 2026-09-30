# Release

## Signing

Release builds are signed when either source provides a keystore:

- `keystore.properties` at the repository root (git-ignored):
  ```
  storeFile=/absolute/path/release.jks
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```
- or environment variables `CF_RELEASE_STORE_FILE`, `CF_RELEASE_STORE_PASSWORD`,
  `CF_RELEASE_KEY_ALIAS`, `CF_RELEASE_KEY_PASSWORD`. In GitHub Actions, store the keystore as
  `CF_RELEASE_KEYSTORE_B64` (base64) plus the three password and alias secrets.

Without either, `assembleRelease` produces an unsigned APK. Keep the keystore and its password
backed up: Android only installs updates signed with the same key. For Play distribution, enroll
in Play App Signing and use this key as the upload key.

## Versioning

`versionName` follows MAJOR.MINOR.PATCH; `versionCode` increments on every release. The build
records the git commit and the Cloudflare schema revision in `BuildConfig`; both appear in
More, About and in the diagnostics export.

## Checklist

1. `python3 tools/ci/secret_scan.py`
2. Regenerate the registry and review the schema diff (see DEVELOPMENT.md).
3. `./gradlew testDebugUnitTest lintRelease assembleRelease`
4. `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
5. Install on a device, connect a token, and walk: Home, a zone's DNS, R2 upload and download,
   API Explorer GET, token detail, app lock.
