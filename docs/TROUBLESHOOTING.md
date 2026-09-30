# Troubleshooting

**"Cloudflare rejected this token."** The token was revoked, expired, or is otherwise invalid.
It stays saved until you remove it in Settings. Connect a new token from the banner.

**A feature I expect is missing from Resources.** The token lacks the permission for it in the
current account or zone. Tap "features hidden" to show them, or open More, What this token can
access, to see what each needs.

**Everything shows but some screens say access was denied.** The token cannot read its own
policies (it lacks API Tokens Read), so the app learns from responses. Add API Tokens Read to
the token for exact permission-aware navigation.

**"Not available on this plan."** Cloudflare reported that the account's plan or entitlements
limit this feature. Changing token permissions will not help.

**"Cloudflare rate limit reached."** Cloudflare allows 1,200 requests per 5 minutes per token and
300 GraphQL queries per 5 minutes by default. The app waits for the delay Cloudflare requests;
long waits are reported instead of blocking.

**Uploads over 300 MB are rejected.** Cloudflare's REST upload limit is 300 MB. Add R2 S3
credentials (an R2 API token's Access Key ID and Secret Access Key) from the bucket's menu to
use resumable multipart uploads.

**A transfer says "Waiting for Wi-Fi".** The transfer was queued as Wi-Fi only. It starts when
an unmetered network is available.

**An account token (`cfat_`) shows "Token verification: Fail" in Diagnostics.** Account-owned
tokens are verified per account, not with `/user/tokens/verify`. The app handles this when
connecting; the diagnostic check reports the user endpoint's answer.

**Sharing a report.** More, Diagnostics, Export sanitized report. It contains app and device
versions, network state and recent request metadata with identifiers replaced, and no tokens,
secrets, headers or bodies.
