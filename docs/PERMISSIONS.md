# Permissions

The app never hardcodes Cloudflare's permission catalog as its source of truth.

## Where permission knowledge comes from

1. **Cloudflare's schema.** Each operation in the generated registry carries the permission
   groups Cloudflare accepts for it (`x-api-token-group`). An operation is authorized if the
   token holds any one of them.
2. **The token's own policies.** When the token can read itself (`API Tokens Read`), the app
   loads its policies: allow and deny grants, each with the accounts and zones it covers.
3. **Observed responses.** Every 2xx and 403 is matched to its schema operation. Without
   readable policies this is the only source, so availability is learned as the app is used.
   Nothing is probed with write requests.
4. **The live permission catalog.** Token creation always fetches
   `/user/tokens/permission_groups` or `/accounts/{id}/tokens/permission_groups`, and sends only
   permission group IDs, since names can change cosmetically.

## Evaluation

For an operation and a concrete account or zone:

- a 403 observed for that operation means `TOKEN_RESTRICTED`, a 2xx means available;
- otherwise, with policies known, the grants whose permission matches the operation are
  filtered to those whose resources cover the account or zone; any deny wins, any allow makes
  it available (`AVAILABLE_WRITE` for Write, Edit, Admin, Revoke and Cache Purge permissions);
- without policies or schema permissions the state is `UNKNOWN`, and the feature is shown.

Screens ask `CapabilityRepository` rather than deciding on their own. Each native screen is
bound to a representative schema operation in `CapabilityProbes`; `EndpointRegistryTest`
fails if a binding stops resolving after a schema update.

## What users see

- Resources hides features the token cannot use, with a count and an option to show them.
- Read-only features are labelled Read-only.
- "What this token can access" lists the token's granted permissions and each feature's state.
- A 403 inside a screen explains that the token may lack the permission, and a plan-related
  403 is reported as a plan limitation instead.

## Keeping up with Cloudflare

CI regenerates the registry from Cloudflare's latest schema every week and on every change,
reports added, removed and changed operations and permission changes, and fails if an operation
the app calls natively was removed or gained a required parameter.
