# Live end-to-end results

Run on 2026-10-01 with `LiveCloudflareE2ETest` against a real account (one account, three
Free-plan zones) using an API token with write access to every account and zone permission
group except Cache Purge, and read access to API tokens. Identifiers are omitted here.

## Summary

| Area | Result |
| --- | --- |
| Token verification and policy discovery | Pass. Policies read from the token: 2,652 operations writable, 116 read-only, of 3,636 |
| Native repository reads (every `list*`/`get*` callable with an account or zone) | 100 parsed real responses, 0 parse failures, 18 refused by Cloudflare for products the account isn't entitled to |
| Schema-wide GET sweep (726 operations through the generic action path) | 463 returned 200; every 401/403 was a product, plan or entitlement refusal; 3 Cloudflare-side 5xx |
| Drill-down from a list item to its detail operation | 39 of 42 resolved the right identifier and loaded |
| Typed-command lifecycles | 23 of 23 steps pass |
| Zone write paths with no-op values | 4 settings re-saved; purge correctly predicted as not allowed and refused by Cloudflare |
| Workers AI | Prompt run passes; 3 of 3 free-form requests planned to the right operation |
| Command search on live names | 27 of 27 phrases resolve to a valid request or screen |

## Lifecycles exercised

Each step runs the typed command through `CommandEngine`, then the resulting draft through the
real `ActionViewModel` (validation, request building, run, result parsing), exactly as tapping
Run does.

- `create kv namespace …`, then write, read and list a key with the KV screen's repository,
  then delete from the action screen
- `create d1 database …`, run `CREATE TABLE`, `INSERT`, `SELECT` through the D1 console's
  repository, delete
- `create r2 bucket …`, delete
- `create queue …`, delete
- `create tunnel …`, delete
- `create ip list …`, delete
- `create turnstile widget … for www.<zone>`, delete
- `create service token …`, delete
- `add txt record _cfctl-e2e-… "…" on <zone>`, confirm the DNS screen's repository sees it,
  list it with a query, edit its content through the list's PATCH follow-up (prefilled from
  the item), delete

## Bugs the live run found and fixed

| Bug | Effect before | Fix |
| --- | --- | --- |
| `errors: null` in Cloudflare's envelope rejected by a non-null property | Queues, Durable Objects, Worker custom domains and zone hold screens always failed to load | Envelope `errors` and `messages` are nullable |
| `result_info.per_page` of 9223372036854775807 overflowed an `Int` | Load Balancing (load balancers, pools, monitors) always failed to load | `perPage` is a `Long` |
| `success: true` with `result: null` treated as a failure | Writing a KV value reported an error after succeeding; 103 write and delete calls affected | Unit calls succeed on `success: true`; list calls treat a null result as empty (95 calls) |
| "No entrypoint ruleset" (code 10003) shown as an error | Snippets and Cloud Connector showed an error until the first rule existed | Mapped to an empty list |
| Paged calls didn't catch parse exceptions | A malformed page could crash the screen | Surfaced as an error |
| Purge offered to tokens without Cache Purge | Tapping it failed with "Authentication error" | Zone hub disables it with the reason; commands flag it as not allowed |

## Refusals that are Cloudflare's, not the app's

Stream, Images, Spectrum, Magic WAN and Magic Transit, Pipelines, Cloudforce One, Brand
Protection, Email Security, Containers, Workers for Platforms, Pay per crawl, Advanced
Certificate Manager and SSL for SaaS, Argo, Cache Reserve and regional tiered cache answered
401 or 403 because the account or plan isn't entitled to them. Cloudflare uses 401 for some
of these, so a 401 alone is not treated as a rejected token.

## Rate limits

The test container's shared egress IP was intermittently throttled at Cloudflare's edge
(HTTP 429, code 971) while the token's own budget showed 1,199 of 1,200 requests left. The
suite retries those briefly in a test-only interceptor; the app's own retry policy is
unchanged and hands long waits back to the UI.
