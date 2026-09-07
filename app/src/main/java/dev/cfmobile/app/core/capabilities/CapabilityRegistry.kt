package dev.cfmobile.app.core.capabilities

import dev.cfmobile.app.ui.navigation.Routes

/**
 * The Cloudflare Capability Registry (PRD §33, §80, §81). This is the canonical list of
 * Cloudflare products/features this app knows about, whether each is implemented, and its
 * roadmap priority - it is the source of truth the zone menu (and eventually account/global
 * navigation) renders from, rather than a hard-coded screen list.
 *
 * Coverage rule (PRD §81): the goal is never to claim 100% API parity on day one. It's to
 * track every product honestly, implement high-value mobile-safe operations first, and never
 * label something supported that isn't.
 */
object CapabilityRegistry {

    val zoneCapabilities: List<Capability> = listOf(
        Capability(
            id = "dns.records",
            product = "DNS",
            displayName = "DNS Records",
            description = "A, AAAA, CNAME, MX, TXT, NS, CAA and more",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.dns(id, name) }
        ),
        Capability(
            id = "ssl.tls",
            product = "SSL/TLS",
            displayName = "SSL/TLS",
            description = "Encryption mode, HTTPS, TLS version, security level",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.ssl(id, name) }
        ),
        Capability(
            id = "firewall.legacy",
            product = "Firewall",
            displayName = "Firewall",
            description = "Firewall rules and IP access rules",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.firewall(id, name) }
        ),
        Capability(
            id = "waf.rulesets",
            product = "WAF",
            displayName = "WAF Custom Rules",
            description = "Modern custom rules via the Rulesets engine (replaces legacy Firewall Rules)",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.waf(id, name) },
            migrationHint = "Covers WAF Custom Rules (the http_request_firewall_custom phase). Cloudflare's own managed rulesets are a separate phase and have their own screen - see the Managed WAF capability. Not verified against a live API call."
        ),
        Capability(
            id = "rate_limiting",
            product = "Rate Limiting",
            displayName = "Rate Limiting",
            description = "Threshold-based rate limiting rules",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.rateLimiting(id, name) }
        ),
        Capability(
            id = "transform_rules",
            product = "Transform Rules",
            displayName = "Transform Rules",
            description = "URL rewrites and request/response header modification",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.transformRules(id, name) },
            migrationHint = "Covers URL Rewrite and request/response header rules only. Origin Rules, Redirect Rules, and Snippets aren't yet manageable from this app - and unlike WAF/Rate Limiting, this request format hasn't been verified against a live API call, only Cloudflare's published rule schema."
        ),
        Capability(
            id = "redirect_rules",
            product = "Rules",
            displayName = "Redirect Rules",
            description = "Dynamic URL redirects driven by expressions",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { zoneId, zoneName -> Routes.redirectRules(zoneId, zoneName) },
            migrationHint = "Single Redirects only, in the http_request_dynamic_redirect phase: create, edit, enable, and delete rules with a static or expression target. Bulk Redirects are an account-level list product and aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "origin_rules",
            product = "Rules",
            displayName = "Origin Rules",
            description = "Override origin host, port, Host header, and SNI",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.originRules(zoneId, zoneName) },
            migrationHint = "Origin host, port, Host header, and SNI overrides. DNS-record-level origin settings and mTLS certificate selection aren't part of this phase and aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "cache_rules",
            product = "Rules",
            displayName = "Cache Rules",
            description = "Per-request cache eligibility and TTLs",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { zoneId, zoneName -> Routes.cacheRules(zoneId, zoneName) },
            migrationHint = "Cache eligibility plus edge and browser TTL modes. Custom cache keys, status-code-specific TTLs, serve-stale, and Cache Reserve aren't implemented - each is a form of its own. Not verified against a live API call."
        ),
        Capability(
            id = "managed_waf",
            product = "Security",
            displayName = "Managed WAF",
            description = "Deploy and disable Cloudflare's managed rulesets",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.managedWaf(zoneId, zoneName) },
            migrationHint = "Shows which managed rulesets are deployed on the zone and lets you deploy, disable, or remove one whole ruleset. Per-rule overrides, sensitivity and paranoia levels, and scoping a deployment to an expression aren't implemented - a deployment made here applies to all traffic. Not verified against a live API call."
        ),
        Capability(
            id = "page_rules",
            product = "Page Rules",
            displayName = "Page Rules",
            description = "URL-based configuration overrides",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.pageRules(id, name) },
            deprecated = true,
            migrationHint = "Page Rules are a legacy configuration surface. For new configuration, consider Rules, Redirects, Origin Rules, or Transform Rules."
        ),
        Capability(
            id = "caching",
            product = "Caching",
            displayName = "Caching",
            description = "Cache level, development mode, browser cache TTL, purge",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.caching(id, name) }
        ),
        Capability(
            id = "certificates",
            product = "SSL/TLS",
            displayName = "Certificates & DNSSEC",
            description = "Edge certificates, custom hostnames, DNSSEC",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.certificates(id, name) },
            migrationHint = "Edge certificate packs are read-only; custom hostnames (SSL for SaaS) can be added and removed; DNSSEC can be turned on and its DS record copied. Uploading a custom certificate, ordering advanced packs, and client certificates aren't implemented. Enabling DNSSEC only generates the DS record - your registrar still has to publish it. Not verified against a live API call."
        ),
        Capability(
            id = "waiting_room",
            product = "Traffic",
            displayName = "Waiting Room",
            description = "Queue visitors ahead of a busy page",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.waitingRoom(id, name) },
            migrationHint = "Create, list, and delete rooms with the core queueing thresholds. Custom queue pages, event scheduling, and per-room rules aren't implemented. Requires a plan that includes Waiting Room. Not verified against a live API call."
        ),
        Capability(
            id = "health_checks",
            product = "Traffic",
            displayName = "Health Checks",
            description = "Standalone origin monitors",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.healthChecks(id, name) },
            migrationHint = "Create, list, and delete standalone zone health checks. Advanced probe tuning (intervals, retries, expected codes, custom headers) uses Cloudflare's defaults here. Not verified against a live API call."
        ),
        Capability(
            id = "speed",
            product = "Speed",
            displayName = "Speed & Optimization",
            description = "Polish, Brotli, Early Hints, Rocket Loader, HTTP/3",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.LOW,
            zoneRoute = { id, name -> Routes.speed(id, name) },
            migrationHint = "The zone-level speed toggles. Settings your plan doesn't include are shown as unavailable rather than as off. Image Resizing and per-URL optimisation rules aren't covered. Not verified against a live API call."
        ),
        Capability(
            id = "network_settings",
            product = "Network",
            displayName = "Network",
            description = "WebSockets, IPv6, gRPC, IP geolocation, Onion Routing",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.network(id, name) },
            migrationHint = "Zone-level network toggles. Not verified against a live API call."
        ),
        Capability(
            id = "scrape_shield",
            product = "Security",
            displayName = "Scrape Shield",
            description = "Email obfuscation, hotlink protection, server-side excludes",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.LOW,
            zoneRoute = { id, name -> Routes.scrapeShield(id, name) },
            migrationHint = "Not verified against a live API call."
        ),
        Capability(
            id = "cache_behaviour",
            product = "Caching",
            displayName = "Cache Behaviour",
            description = "Always Online, crawler hints, query string sort, browser check",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.cacheBehaviour(id, name) },
            migrationHint = "The zone-setting side of caching; cache level, browser TTL, development mode and purge live under Caching. Tiered Cache, Cache Reserve, and Argo have their own endpoints and aren't covered. Not verified against a live API call."
        ),
        Capability(
            id = "snippets",
            product = "Develop",
            displayName = "Snippets",
            description = "Small JavaScript programs run on matching requests",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.snippets(zoneId, zoneName) },
            migrationHint = "Lists snippets, shows a snippet's source read-only, deletes one, and enables, disables, or deletes the rules that decide when a snippet runs. Uploading snippet code isn't implemented - the same limit as Workers, and Cloudflare wants the code as a multipart upload alongside a metadata document. Creating a rule or reordering rules isn't implemented either: Cloudflare replaces the zone's whole rule list on write. Not verified against a live API call."
        ),
        Capability(
            id = "cloud_connector",
            product = "Rules",
            displayName = "Cloud Connector",
            description = "Route matching requests to object storage",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.cloudConnector(zoneId, zoneName) },
            migrationHint = "Create, edit, enable, and delete rules pointing matching requests at R2, S3, Azure, or Google Cloud Storage. Cloudflare replaces the zone's whole rule list on every write, so a change made here from a stale list would drop rules added elsewhere in between - the response is used as the new truth. Rule ordering isn't editable. Not verified against a live API call."
        ),
        Capability(
            id = "custom_pages",
            product = "Security",
            displayName = "Error Pages",
            description = "Custom error and challenge pages",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { zoneId, zoneName -> Routes.customPages(zoneId, zoneName) },
            migrationHint = "Points a page type at HTML you host over HTTPS, or reverts it to Cloudflare's default. The page's HTML lives at that URL, not in this app, and Cloudflare requires it to contain the substitution tokens it lists - which this app shows but can't check for you. Not verified against a live API call."
        ),
        Capability(
            id = "zaraz",
            product = "Analytics",
            displayName = "Zaraz",
            description = "Third-party tools loaded on the edge",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            zoneRoute = { zoneId, zoneName -> Routes.zaraz(zoneId, zoneName) },
            migrationHint = "Read-only: which tools and triggers are configured, and which parts of a visitor's request Zaraz forwards to them. Editing isn't implemented because Cloudflare replaces the entire configuration document on write and this app models only part of it - a save would risk dropping fields it never parsed. Publishing and the configuration history aren't implemented either. Not verified against a live API call."
        ),
        Capability(
            id = "analytics",
            product = "Analytics",
            displayName = "Analytics",
            description = "Requests, bandwidth, threats, unique visitors",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            zoneRoute = { id, name -> Routes.analytics(id, name) }
        ),
        Capability(
            id = "security_events",
            product = "Security",
            displayName = "Security Events",
            description = "WAF/firewall event explorer with filtering",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            zoneRoute = { id, name -> Routes.securityEvents(id, name) },
            migrationHint = "Read-only event explorer over Cloudflare's GraphQL firewallEventsAdaptive dataset, filterable by time range. Event retention and which fields are queryable both depend on the zone's plan, so an empty list can mean the plan doesn't retain events that far back rather than that nothing happened. Not verified against a live API call."
        ),
        Capability(
            id = "audit_logs",
            product = "Audit",
            displayName = "Audit Logs",
            description = "Who changed what, and when",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            accountRoute = { accountId -> Routes.auditLogs(accountId) },
            migrationHint = "Uses Cloudflare's classic audit_logs endpoint; a newer Unified Audit Logs API may eventually supersede it for some account types. Not verified against a live API call."
        ),
        Capability(
            id = "load_balancing",
            product = "Traffic",
            displayName = "Load Balancing & Health Checks",
            description = "Load balancers, pools, origins",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.loadBalancing(accountId) },
            migrationHint = "Pools, load balancers, and HTTP health monitors, with a monitor attachable when a pool is created. TCP and UDP monitors, editing a pool's monitor after creation, and multi-pool steering (priority, geo, or weighted) aren't implemented - each load balancer here uses a single pool for both its default and fallback. Not verified against a live API call."
        ),
        Capability(
            id = "page_shield",
            product = "Security",
            displayName = "Page Shield",
            description = "Client-side script and connection detection",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            zoneRoute = { id, name -> Routes.pageShield(id, name) },
            migrationHint = "The on/off setting, the detected scripts and outbound connections, and policies that allow-list scripts on matching pages. Per-script alerting and the script-change history aren't implemented. Both policy fields are raw Cloudflare filter expressions - this app doesn't build them for you. Not verified against a live API call."
        ),
        Capability(
            id = "api_shield",
            product = "Security",
            displayName = "API Shield",
            description = "API discovery, schema validation, mTLS",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            zoneRoute = { id, name -> Routes.apiShield(id, name) },
            migrationHint = "Read-only list of endpoints discovered from real traffic. Schema validation, mTLS certificates, and per-operation rate limits aren't managed here. Requires an API Shield-enabled plan. Not verified against a live API call."
        ),
        Capability(
            id = "bot_management",
            product = "Security",
            displayName = "Bot Management",
            description = "Bot score insights and mitigation",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            zoneRoute = { id, name -> Routes.botManagement(id, name) },
            migrationHint = "Bot Fight Mode plus Super Bot Fight Mode's per-category actions (definitely and likely automated, verified bots), static resource protection, and the WordPress optimization. Which of these appear depends on the zone's plan - Cloudflare omits the fields a plan doesn't include, and a save only ever changes fields the zone itself reported. Bot analytics and per-rule bot scores aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "ddos",
            product = "Security",
            displayName = "DDoS Protection",
            description = "Status, events, and mitigations",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            zoneRoute = { id, name -> Routes.ddos(id, name) },
            migrationHint = "Read-only view of the managed HTTP DDoS ruleset and any overrides on it. Cloudflare's L7 DDoS protection is always on and can't be disabled; changing rule sensitivity isn't offered here because that decision needs more context than a phone screen gives. Not verified against a live API call."
        ),
        Capability(
            id = "workers",
            product = "Develop",
            displayName = "Workers",
            description = "Deploy, inspect, and manage Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.workers(accountId) },
            migrationHint = "List, inspect (source, cron triggers), and delete. Editing or deploying script code needs an editor and bundler that don't belong on mobile, so that isn't implemented; bindings, secrets, versions, and tail logs aren't covered either. A module Worker's source is shown as the multipart parts Cloudflare returns. Not verified against a live API call."
        ),
        Capability(
            id = "worker_routes",
            product = "Develop",
            displayName = "Worker Routes",
            description = "Map URL patterns on a zone to Workers",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.workerRoutes(zoneId, zoneName) },
            migrationHint = "Create, edit, and delete routes that bind a URL pattern on this zone to a Worker. Routes attached to a Worker from the account side (the newer per-script routes API) and custom domains aren't covered. Not verified against a live API call."
        ),
        Capability(
            id = "pages",
            product = "Develop",
            displayName = "Pages",
            description = "Static site and full-stack deployments",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            accountRoute = { accountId -> Routes.pages(accountId) },
            migrationHint = "List projects, view deployment history, redeploy the production branch, and retry a failed deployment. Editing project or build configuration, uploading assets directly, and managing custom domains aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "r2",
            product = "Storage & Media",
            displayName = "R2",
            description = "Object storage buckets",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.r2(accountId) },
            migrationHint = "Bucket management only (create/list/delete) - browsing or uploading objects inside a bucket isn't implemented, that's a separate file-browser-sized surface. Not verified against a live API call."
        ),
        Capability(
            id = "kv",
            product = "Develop",
            displayName = "KV",
            description = "Key-value storage for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.kv(accountId) },
            migrationHint = "Namespaces plus browsing, editing, and deleting the keys inside one. Values are handled as UTF-8 text: a binary value is reported rather than shown, and writing metadata, expirations, or bulk operations isn't implemented. The key list isn't paginated, so a very large namespace shows only Cloudflare's first page. Not verified against a live API call."
        ),
        Capability(
            id = "d1",
            product = "Develop",
            displayName = "D1",
            description = "Serverless SQL database",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.d1(accountId) },
            migrationHint = "Databases plus a SQL console that runs arbitrary statements, with a confirmation before anything that writes. Results are shown as a scrollable table; exporting results, time-travel restore, and migrations aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "queues",
            product = "Develop",
            displayName = "Queues",
            description = "Message queues for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.queues(accountId) },
            migrationHint = "Queue management only (create/list/delete) - producing and consuming messages is a Worker's job, so there's no message browser. Not verified against a live API call."
        ),
        Capability(
            id = "access",
            product = "Zero Trust",
            displayName = "Access",
            description = "Applications, policies, identity providers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.access(accountId) },
            migrationHint = "Applications with one inline policy each, covering the common allow/block by email domain or specific addresses cases. Multi-policy apps and non-email include rules (groups, IP ranges, device posture) aren't implemented. Login methods and service tokens have their own screen - see the Access Identity capability. Not verified against a live API call."
        ),
        Capability(
            id = "access_identity",
            product = "Zero Trust",
            displayName = "Access Identity",
            description = "Login methods and service tokens",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.accessIdentity(accountId) },
            migrationHint = "Lists every identity provider and lets you add Cloudflare's one-time PIN or delete any of them; an external provider (Google, Okta, SAML, ...) can't be created or edited here, because that means handling its client credentials, which this app deliberately never asks for. Service tokens can be created, listed, and deleted - the client secret is shown once, right after creation, and is never stored. Rotating a token isn't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "gateway",
            product = "Zero Trust",
            displayName = "Gateway",
            description = "Network, HTTP, and DNS policies",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.gateway(accountId) },
            migrationHint = "Block/allow policies for the DNS, HTTP, and network engines, each matching one hostname or destination IP. Richer Wirefilter expressions (categories, identity, device posture, list references), rule ordering, and editing an existing policy aren't implemented. Lists have their own screen - see the Gateway Lists capability. Not verified against a live API call."
        ),
        Capability(
            id = "gateway_lists",
            product = "Zero Trust",
            displayName = "Gateway Lists",
            description = "Reusable domain, URL, IP, serial, and email lists",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            accountRoute = { accountId -> Routes.gatewayLists(accountId) },
            migrationHint = "Create a list with its entries pasted one per line, view a list's entries, and delete a list. Editing an existing list's entries isn't implemented - Cloudflare models that as a bulk patch, and doing it a row at a time on a phone would be worse than recreating the list. Not verified against a live API call."
        ),
        Capability(
            id = "tunnels",
            product = "Zero Trust",
            displayName = "Tunnels",
            description = "Cloudflare Tunnel inventory and status",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.tunnels(accountId) },
            migrationHint = "List/create/delete only - this registers a tunnel with Cloudflare, it doesn't run one. Actually connecting traffic through it needs the cloudflared daemon on a machine elsewhere, which is out of scope for a mobile app. Not verified against a live API call."
        ),
        Capability(
            id = "device_posture",
            product = "Zero Trust",
            displayName = "Devices & Posture",
            description = "Enrolled devices and posture checks",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            accountRoute = { accountId -> Routes.devicePosture(accountId) },
            migrationHint = "Inventory of enrolled devices and posture rules, plus revoking a device's registration behind a confirmation. Editing a posture rule isn't implemented - each rule type carries its own configuration shape. Not verified against a live API call."
        ),
        Capability(
            id = "account_members",
            product = "Account",
            displayName = "Members & Roles",
            description = "Account membership, roles, invitations",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.accountMembers(accountId) },
            migrationHint = "This request format (member invite/remove, roles) hasn't been verified against a live API call, only against Cloudflare's documented schema - unlike WAF/Rate Limiting, which were."
        ),
        Capability(
            id = "durable_objects",
            product = "Develop",
            displayName = "Durable Objects",
            description = "Stateful Workers coordination",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.durableObjects(accountId) },
            migrationHint = "Read-only namespace inventory. Namespaces are created and removed by deploying a Worker that declares them, and inspecting an individual object's stored state isn't exposed by the API. Not verified against a live API call."
        ),
        Capability(
            id = "workflows",
            product = "Develop",
            displayName = "Workflows",
            description = "Durable multi-step Workers execution",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.workflows(accountId) },
            migrationHint = "Lists deployed Workflows and their recent instances, starts a run, and pauses, resumes, or terminates one - only the transitions Cloudflare accepts for a run's current state are offered, and terminating is confirmed first. Passing parameters to a run, and a run's step-by-step history, aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "hyperdrive",
            product = "Develop",
            displayName = "Hyperdrive",
            description = "Database connection pooling for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.hyperdrive(accountId) },
            migrationHint = "List and delete only. Creating a config requires entering a database password, which shouldn't be typed into a phone - use wrangler or the dashboard for that. Not verified against a live API call."
        ),
        Capability(
            id = "ai",
            product = "Develop",
            displayName = "Workers AI",
            description = "Run AI models at the edge",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.workersAi(accountId) },
            migrationHint = "Read-only model catalogue. Running inference belongs in a Worker and bills per request, so this browses the available models rather than invoking them. Not verified against a live API call."
        ),
        Capability(
            id = "vectorize",
            product = "Develop",
            displayName = "Vectorize",
            description = "Vector database for AI workloads",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.vectorize(accountId) },
            migrationHint = "Index management only (create/list/delete) - inserting or querying vectors is a Worker's job, so there's no vector browser. Not verified against a live API call."
        ),
        Capability(
            id = "stream",
            product = "Storage & Media",
            displayName = "Stream",
            description = "Video hosting and delivery",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.stream(accountId) },
            migrationHint = "List, inspect, delete, and upload a video from the device through Android's photo picker. The upload sends the whole file in one request, which is Cloudflare's 200 MB limit - larger files need the tus resumable protocol, which isn't implemented, so an oversized pick is refused before it starts rather than after a long upload fails. There is no upload progress for the same reason, and playback happens in Cloudflare's player, not in this app. Not verified against a live API call."
        ),
        Capability(
            id = "images",
            product = "Storage & Media",
            displayName = "Images",
            description = "Image storage, resizing, delivery",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.images(accountId) },
            migrationHint = "Inventory, quota, delete, and uploading a picture from the device through Android's photo picker - which needs no storage permission and only ever hands the app the one file you choose. Variant configuration, signed-URL delivery, and uploading by URL aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "email_routing",
            product = "Network",
            displayName = "Email Routing",
            description = "Custom email addresses and routing rules",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.emailRouting(id, name) },
            migrationHint = "Status, forwarding rules, destination addresses, and the catch-all. Adding a destination sends Cloudflare's verification email; the app shows whether an address has been verified but can't click the link for you, and an unverified address won't receive mail. The catch-all here forwards to a verified destination or is off - \"drop\", which discards unmatched mail silently, isn't offered. Email Workers routing and editing an existing rule aren't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "turnstile",
            product = "Security",
            displayName = "Turnstile",
            description = "CAPTCHA alternative widget management",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.turnstile(accountId) },
            migrationHint = "Widget create/list/delete. Only the public sitekey is shown or copied - this app never reads a widget's secret key. Rotating a secret isn't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "spectrum",
            product = "Network",
            displayName = "Spectrum",
            description = "TCP/UDP application proxying",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { id, name -> Routes.spectrum(id, name) },
            migrationHint = "List and delete only. Creating an application means choosing origin, protocol, edge IP and TLS settings together - a desktop-sized form. Spectrum needs a plan that includes it. Not verified against a live API call."
        ),
        Capability(
            id = "magic_network",
            product = "Network",
            displayName = "Magic Firewall / WAN / Transit",
            description = "Network-layer routing and protection",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.magicNetwork(accountId) },
            migrationHint = "Read-only inventory of GRE tunnels, IPsec tunnels, and static routes. Changing network routing isn't offered from a phone, and Magic Firewall rulesets aren't covered. These are Enterprise features, so most accounts will see empty lists. Not verified against a live API call."
        ),
        Capability(
            id = "logpush",
            product = "Analytics & Logs",
            displayName = "Logpush",
            description = "Bulk log export administration",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.logpush(accountId) },
            migrationHint = "Account-level jobs: list, pause/resume, delete. Creating a job needs a destination string embedding storage credentials, so that stays in the dashboard; destinations shown here are truncated before their query string for the same reason. Zone-level jobs aren't listed. Not verified against a live API call."
        ),
        Capability(
            id = "api_tokens",
            product = "Account",
            displayName = "API Tokens",
            description = "Review and revoke the account's API tokens",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.apiTokens(accountId) },
            migrationHint = "Lists token metadata - name, status, issue and expiry dates, last use - and revokes a token. Creating or rolling a token isn't implemented: both return a token value, and building the permission-policy editor they need is a desktop-sized job. No token value is ever fetched or displayed, including the one this app signs in with. Reading this list needs a token carrying User API Tokens Read, which many tokens don't. Not verified against a live API call."
        ),
        Capability(
            id = "notifications",
            product = "Account",
            displayName = "Notifications",
            description = "Alert policies and where they notify",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            accountRoute = { accountId -> Routes.notifications(accountId) },
            migrationHint = "Lists alert policies, silences or re-enables one, and deletes one. Creating a policy or editing its destinations isn't implemented - Cloudflare's alert types each carry their own filter shape. Destination counts are shown rather than the addresses themselves. Not verified against a live API call."
        ),
        Capability(
            id = "bulk_redirects",
            product = "Rules",
            displayName = "Bulk Redirects",
            description = "Account-wide redirect lists",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.bulkRedirects(accountId) },
            migrationHint = "Lists redirect lists, shows the redirects inside one, creates an empty list, and deletes a list. Uploading redirects into a list isn't implemented - Cloudflare runs that as an asynchronous bulk operation with its own status polling. Deploying a list through the Bulk Redirect ruleset isn't implemented either. Not verified against a live API call."
        ),
        Capability(
            id = "registrar",
            product = "Account",
            displayName = "Registrar",
            description = "Domains registered through Cloudflare",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.registrar(accountId) },
            migrationHint = "Read-only: registration status, expiry, auto-renew, and transfer lock. Every write this API offers either authorizes a charge (renewal, auto-renew) or moves a domain between registrars, so none is implemented - the same reason the Billing screen can't change a plan. Not verified against a live API call."
        ),
        Capability(
            id = "web_analytics",
            product = "Analytics",
            displayName = "Web Analytics",
            description = "Privacy-first RUM sites and their beacons",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            accountRoute = { accountId -> Routes.webAnalytics(accountId) },
            migrationHint = "Lists RUM sites, adds one (auto-installed or with a snippet to copy), and deletes one. The analytics themselves aren't shown here - that's a GraphQL dataset of its own - and editing a site's host or install mode isn't implemented. Not verified against a live API call."
        ),
        Capability(
            id = "billing",
            product = "Account",
            displayName = "Billing & Plan",
            description = "Current plan and usage - no payment changes",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            requiresBrowserHandoff = true,
            accountRoute = { accountId -> Routes.billing(accountId) },
            migrationHint = "Strictly read-only: current subscriptions and their state. Changing a plan or touching payment details always hands off to Cloudflare's dashboard - money-moving actions belong behind Cloudflare's own confirmation flow, not a phone tap. Not verified against a live API call."
        ),
        Capability(
            id = "browser_rendering",
            product = "Develop",
            displayName = "Browser Rendering",
            description = "Headless browser automation",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            accountRoute = { accountId -> Routes.browserRendering(accountId) },
            migrationHint = "A screenshot utility rather than a management surface - Browser Rendering is a runtime API with nothing to administer. Enter a URL and Cloudflare renders the page at the edge. PDF rendering, scraping, and Puppeteer sessions aren't exposed. Each render bills against the account. Not verified against a live API call."
        )
    )

    fun implemented(): List<Capability> = zoneCapabilities.filter { it.status == CapabilityStatus.IMPLEMENTED }

    fun notYetImplemented(): List<Capability> = zoneCapabilities.filter { it.status != CapabilityStatus.IMPLEMENTED }

    fun byId(id: String): Capability? = zoneCapabilities.firstOrNull { it.id == id }

    fun implementedForScope(scope: CapabilityScope): List<Capability> =
        implemented().filter { it.scope == scope }

    fun notYetImplementedForScope(scope: CapabilityScope): List<Capability> =
        notYetImplemented().filter { it.scope == scope }

    /**
     * Account-scoped capabilities the Dashboard renders, grouped under their product area.
     * Grouping preserves registry order (rather than sorting alphabetically) so related
     * products stay adjacent, and [groupBy] on a List keeps first-appearance order for us.
     */
    fun accountMenu(): List<Pair<String, List<Capability>>> =
        implementedForScope(CapabilityScope.ACCOUNT)
            .groupBy { it.product }
            .toList()
}
