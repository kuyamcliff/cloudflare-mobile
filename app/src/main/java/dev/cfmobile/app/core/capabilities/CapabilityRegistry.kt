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
            id = "legacy_firewall",
            product = "Security",
            displayName = "Lockdown & User Agents",
            description = "Zone Lockdown and User Agent Blocking",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.legacyFirewall(zoneId, zoneName) },
            migrationHint = "Cloudflare's pre-Rulesets firewall products, both with full CRUD. A lockdown's source is read as an IP, a CIDR range, or a two-letter country code from the shape of what you type, matching how Cloudflare picks the target. A User Agent rule matches the header exactly - wildcards belong in a WAF custom rule, and the form says so rather than letting one silently match nothing. Not verified against a live API call."
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
            id = "config_rules",
            product = "Rules",
            displayName = "Config Rules",
            description = "Override a zone setting for matching traffic",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { zoneId, zoneName -> Routes.configRules(zoneId, zoneName) },
            migrationHint = "Create, edit, enable, and delete rules that override one zone setting for matching requests. Cloudflare allows several settings in a single rule; this form edits one per rule, which is the honest fit for a phone. The SSL-mode override and Polish level aren't offered because they take a value rather than a switch. Not verified against a live API call."
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
            id = "client_certificates",
            product = "SSL/TLS",
            displayName = "Client Certificates",
            description = "mTLS certificates, origin pulls, and Total TLS",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.clientCertificates(zoneId, zoneName) },
            migrationHint = "Lists and revokes the client certificates a zone accepts for mTLS, and toggles Authenticated Origin Pulls and Total TLS. Uploading a certificate isn't implemented: it means generating a CSR and holding a private key, which this app deliberately never does. Per-hostname origin pull certificates and Keyless SSL aren't covered. Not verified against a live API call."
        ),
        Capability(
            id = "zone_ownership",
            product = "Account",
            displayName = "Zone Ownership",
            description = "Zone hold and custom nameservers",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            zoneRoute = { zoneId, zoneName -> Routes.zoneOwnership(zoneId, zoneName) },
            migrationHint = "Places or removes the hold that stops this domain being added to another Cloudflare account, and picks which account nameserver set the zone answers on. Removing a hold is confirmed, since that's what lets someone else claim the domain. Creating nameserver sets is an account-level operation and isn't implemented here. Not verified against a live API call."
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
            migrationHint = "Create, list, and delete rooms with the core queueing thresholds. Tapping a room opens its scheduled events, which can be listed, created, and deleted - an event overrides the room's thresholds for one window, and blank overrides mean 'keep the room's own'. Times are entered and shown in UTC, in Cloudflare's own format, rather than converted into a local timezone the API never mentions. Editing a room or an event, custom queue pages, pre-queue windows, and per-room rules aren't implemented. Requires a plan that includes Waiting Room. Not verified against a live API call."
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
            id = "performance",
            product = "Speed",
            displayName = "Routing & Cache",
            description = "Argo, tiered cache, cache reserve, managed transforms",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { zoneId, zoneName -> Routes.performance(zoneId, zoneName) },
            migrationHint = "The routing and caching controls that live outside /zones/{id}/settings and so can't use the declarative settings machinery: Argo Smart Routing and Tiered Caching, Cache Reserve, smart and regional tiered cache, managed header transforms, and URL normalization. A control the zone's plan doesn't include isn't rendered at all - it's listed as unavailable instead of shown as a dead switch. Cache Reserve is billed storage, which the row says. Not verified against a live API call."
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
            migrationHint = "List, inspect (source, cron triggers, recent deployments), delete, and manage secrets - which are write-only in both directions: Cloudflare returns names only, and this app never asks for a value back or stores one. Editing or deploying script code needs an editor and bundler that don't belong on mobile; bindings, version rollback, and tail logs aren't covered. A module Worker's source is shown as the multipart parts Cloudflare returns. Not verified against a live API call."
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
            id = "worker_domains",
            product = "Develop",
            displayName = "Worker Domains",
            description = "Hostnames bound directly to a Worker",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.workerDomains(accountId) },
            migrationHint = "Lists, attaches, and detaches the hostnames bound straight to a Worker, as opposed to routes on a zone. The form picks the zone and the Worker by name rather than asking for ids. Environments other than production aren't selectable. Not verified against a live API call."
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
            migrationHint = "List projects, view deployment history, redeploy the production branch, retry a failed deployment, and add or remove custom domains - Cloudflare verifies a new domain asynchronously, so it shows as pending rather than live. Editing project or build configuration and uploading assets directly aren't implemented. Not verified against a live API call."
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
            migrationHint = "Buckets, plus each bucket's public access, custom domains, CORS policy, and lifecycle rules. Browsing or uploading objects is genuinely out of reach: R2's data plane is the S3-compatible API, which needs its own access key pair rather than this app's Cloudflare token. CORS and lifecycle are read-only apart from removing the CORS policy outright, since editing either replaces the whole document. Connecting a new custom domain is left to the dashboard, which walks through DNS and certificates. Not verified against a live API call."
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
            migrationHint = "Queues plus the consumers pulling from each one, with the batching settings that decide how they're called. Attaching a consumer is part of that Worker's own configuration and isn't something this app can write, so the sheet lists and detaches only. Sending or previewing messages isn't implemented. Not verified against a live API call."
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
            id = "access_directory",
            product = "Zero Trust",
            displayName = "Access Directory",
            description = "Groups, bookmarks, tags, and mTLS roots",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.accessDirectory(accountId) },
            migrationHint = "Reusable Access groups, launchpad bookmarks, application tags, and the root certificates Access accepts client certificates from. A group is created with the same two rule kinds the policy form supports - an email domain and individual addresses; IP ranges, device posture, and service-token rules are set from the dashboard, and a group carrying them shows the count rather than pretending they aren't there. Uploading a root certificate means handling a certificate chain and isn't implemented. Editing an existing group isn't either. Not verified against a live API call."
        ),
        Capability(
            id = "zero_trust_network",
            product = "Zero Trust",
            displayName = "Zero Trust Network",
            description = "Tunnel routes, virtual networks, DNS locations, WARP profiles",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.zeroTrustNetwork(accountId) },
            migrationHint = "The private-network side of Zero Trust: routes reachable through a tunnel, the virtual networks that let overlapping ranges coexist, and Gateway's DNS locations - all with create and delete. Routes and tunnels are picked by name rather than by id. Assigning a route to a non-default virtual network isn't offered. WARP device profiles are read-only: their split tunnel and fallback-domain lists are list editors of their own. Not verified against a live API call."
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
            description = "Videos, live inputs, watermarks, and keys",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.stream(accountId) },
            migrationHint = "Four tabs. Videos lists, deletes, and uploads from the device through Android's photo picker; the upload sends the whole file in one request, which is Cloudflare's 200 MB limit - larger files need the tus resumable protocol, which isn't implemented, so an oversized pick is refused before it starts rather than after a long upload fails. There is no upload progress for the same reason, and playback happens in Cloudflare's player, not in this app. A video's caption tracks open from its row and can be deleted; adding one means uploading a VTT file, which isn't implemented. Live lists and creates inputs - the RTMPS, SRT and WebRTC endpoints and the stream key come from the single-input read, are shown only while that sheet is open, and are never persisted. Watermarks and signing keys are list and delete: creating a watermark uploads a logo, and a signing key's private half is returned once at creation, which this app never asks for. Not verified against a live API call."
        ),
        Capability(
            id = "images",
            product = "Storage & Media",
            displayName = "Images",
            description = "Image storage, variants, and signing keys",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.images(accountId) },
            migrationHint = "Three tabs. Images covers inventory, quota, delete, and uploading a picture from the device through Android's photo picker - which needs no storage permission and only ever hands the app the one file you choose. Variants lists and creates the named sizes a delivery URL can ask for, with Cloudflare's fit modes and an explicit choice about keeping EXIF; editing an existing variant isn't offered, since changing one silently reshapes every image already served through it, and deleting one breaks the delivery URLs that end in its name, which the confirmation says. Keys lists signing key names only - a key's value signs private delivery URLs, so it is dropped in the repository before it can reach the UI, and rotating one isn't implemented. Uploading by URL isn't implemented. Not verified against a live API call."
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
            migrationHint = "Widget create, list, delete, and secret rotation. No call here ever fetches a secret: the list and the row show only the public sitekey, which is also the only value the copy button touches. The single exception is a rotation the user explicitly confirms - Cloudflare answers that call with the newly minted secret, which is the only way to learn the value that replaced the old one, so it is shown once in a dialog and dropped when that dialog closes. It is never persisted or logged. The confirmation says plainly that the old secret stops verifying immediately. Editing a widget's domains or mode isn't implemented. Not verified against a live API call."
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
            displayName = "Magic WAN",
            description = "Tunnels, static routes, and connector sites",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.magicNetwork(accountId) },
            migrationHint = "Four tabs. GRE and IPsec tunnels stay read-only on purpose: a tunnel carries the interface addresses and health checks of a physical link, and a wrong value takes a site offline until someone is at a console. Static routes are list, add and delete - one prefix pointed at one next hop is the change that gets made under pressure, and the delete confirmation says whose traffic stops. Sites are read-only with their LAN and WAN interfaces behind a tap; adding one means registering connector hardware. Editing a route, and a site's interface addressing, aren't implemented. Magic Firewall has its own screen. These are Enterprise features, so most accounts will see empty lists. Not verified against a live API call."
        ),
        Capability(
            id = "magic_firewall",
            product = "Network",
            displayName = "Magic Firewall",
            description = "Packet filtering in front of Magic Transit",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.magicFirewall(accountId) },
            migrationHint = "The Rulesets engine at account scope, phase magic_transit - the same list/add/edit/delete/enable cycle as the zone rule screens. Block, log and allow are the actions Cloudflare offers here; there is no challenge or redirect, since this filter sees packets rather than requests. Expressions are typed as text: there is no field builder, and no validation beyond requiring one. Reordering rules isn't implemented - a new rule is appended after the existing ones - and neither is the managed ruleset deployment that Enterprise accounts can layer on top. Deleting a rule can silently start admitting traffic the rule was dropping, which the confirmation says. Not verified against a live API call."
        ),
        Capability(
            id = "addressing",
            product = "Network",
            displayName = "Addressing",
            description = "BYOIP prefixes and address maps",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.addressing(accountId) },
            migrationHint = "Two tabs. Prefixes lists the account's BYOIP space and switches each prefix's BGP announcement on or off - the one thing here worth doing from a phone. Adding a prefix isn't implemented: it needs a signed letter of authorization and Cloudflare's approval first. The switch is left visible but inert when Cloudflare would refuse the change (prefix not approved, on-demand not enabled, or control locked), with the reason on the row, rather than being hidden or failing on tap. Editing a prefix's description isn't wired to the UI. Address Maps lists maps, creates one, enables or disables one, and deletes one; tapping a map fetches its addresses and bindings, which the list response omits. Adding or removing a map's IPs and its zone or account bindings isn't implemented - each is a separate per-member endpoint and getting the set wrong takes zones off those addresses. Not verified against a live API call."
        ),
        Capability(
            id = "request_tracer",
            product = "Diagnostics",
            displayName = "Request Tracer",
            description = "See which of your rules would match a request",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.NONE,
            accountRoute = { accountId -> Routes.tracer(accountId) },
            migrationHint = "Cloudflare replays a URL and method against its own pipeline and reports every step it evaluated, marking the ones that matched. Nothing reaches the origin, so this is safe against a live site - and for the same reason there is no response body to inspect. Custom request headers, a request body, and the bot- and threat-score overrides the API accepts aren't wired to the form. Not verified against a live API call."
        ),
        Capability(
            id = "web3",
            product = "DNS",
            displayName = "Web3 Gateways",
            description = "IPFS and Ethereum gateways on your own hostname",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.web3(id, name) },
            migrationHint = "Lists gateway hostnames, adds one for IPFS (DNSLink or universal path) or Ethereum, and deletes one. Editing an existing gateway's DNSLink isn't implemented, and neither is the IPFS universal-path content list. Deleting a gateway leaves its DNS record behind, which the confirmation says. Not verified against a live API call."
        ),
        Capability(
            id = "dns_settings",
            product = "DNS",
            displayName = "DNS Settings",
            description = "Zone-wide DNS behaviour, apart from the records",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.MEDIUM,
            zoneRoute = { id, name -> Routes.dnsSettings(id, name) },
            migrationHint = "CNAME flattening, Foundation DNS, multi-provider DNS, and secondary overrides, each sending only its own field so flipping one never rewrites the others. The nameserver assignment and NS TTL are shown but not editable here - switching to custom nameservers belongs to Zone Ownership, which already does it. Secondary-zone transfer settings (peers, TSIG keys, ACLs) aren't implemented. Not verified against a live API call."
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
            description = "Review and revoke user and account API tokens",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.apiTokens(accountId) },
            migrationHint = "Two tabs: the signed-in user's own tokens, and the tokens the account itself owns (the cfat_ kind, which outlive whoever created them). Each shows metadata only - name, status, issue and expiry dates, last use - and can be revoked. Creating or rolling a token isn't implemented: both return a token value, and building the permission-policy editor they need is a desktop-sized job. No token value is ever fetched or displayed, including the one this app signs in with. Each tab needs its own permission (User API Tokens Read, Account API Tokens Read), so one can be readable while the other isn't; each keeps its own error rather than failing the screen. Not verified against a live API call."
        ),
        Capability(
            id = "notifications",
            product = "Account",
            displayName = "Notifications",
            description = "Alert policies, destinations, and what was sent",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.MEDIUM,
            accountRoute = { accountId -> Routes.notifications(accountId) },
            migrationHint = "Three tabs. Alerts lists the policies, silences or re-enables one, and deletes one; creating a policy or editing its destinations isn't implemented - Cloudflare's alert types each carry their own filter shape, and destination counts are shown rather than the addresses themselves. Destinations lists webhook targets and adds or deletes one; a new destination still has to be attached to a policy from the dashboard, PagerDuty needs an OAuth connection, and only a webhook's host is displayed since its path can carry the delivery token. History is read-only and covers whatever window Cloudflare retains. Not verified against a live API call."
        ),
        Capability(
            id = "platform",
            product = "Account",
            displayName = "Platform",
            description = "AI Gateway, Calls, Pipelines, and Secrets Store",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.platform(accountId) },
            migrationHint = "Four account products behind four tabs, each loaded independently so one being unavailable on the plan leaves the others usable. AI Gateway lists gateways and creates one with its cache TTL, rate limit, and log setting; editing a gateway or reading its request logs isn't implemented, and provider keys never pass through this app. Calls lists applications and creates one - the app secret is shown exactly once, in a dialog, and is never persisted or logged. Pipelines is list and delete only: creating one needs a source, a destination bucket, and that bucket's credentials. Secrets Store creates and deletes stores; a secret's value is never readable by any API and this app doesn't write one either, since the bulk create payload differs per scope. Not verified against a live API call."
        ),
        Capability(
            id = "dns_firewall",
            product = "DNS",
            displayName = "DNS Firewall",
            description = "Cloudflare resolvers in front of your own nameservers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2,
            destructiveRisk = DestructiveRisk.HIGH,
            accountRoute = { accountId -> Routes.dnsFirewall(accountId) },
            migrationHint = "Lists clusters with their Cloudflare-side addresses, creates one from a name and a list of upstream IPs, and deletes one. A separate product from the per-zone DNS records elsewhere in the app. Editing an existing cluster and tuning its cache TTLs, rate limit, negative caching, or ECS setting aren't implemented - creation takes Cloudflare's defaults for all of them. Deleting a cluster is destructive in a way a list row can hide: anything still delegating to it stops resolving, which the confirmation says. Not verified against a live API call."
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
