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
            migrationHint = "Covers Custom Rules only for now. Cloudflare Managed Rulesets (e.g. the OWASP Core Ruleset) aren't yet manageable from this app."
        ),
        Capability(
            id = "rate_limiting",
            product = "Rate Limiting",
            displayName = "Rate Limiting",
            description = "Threshold-based rate limiting rules",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.HIGH
        ),
        Capability(
            id = "transform_rules",
            product = "Transform Rules",
            displayName = "Transform & Redirect Rules",
            description = "URL rewrites, header transforms, origin rules, redirects, snippets",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0,
            destructiveRisk = DestructiveRisk.MEDIUM
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
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0
        ),
        Capability(
            id = "audit_logs",
            product = "Audit",
            displayName = "Audit Logs",
            description = "Who changed what, and when",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P0
        ),
        Capability(
            id = "load_balancing",
            product = "Traffic",
            displayName = "Load Balancing & Health Checks",
            description = "Load balancers, monitors, pools, origins",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "page_shield",
            product = "Security",
            displayName = "Page Shield",
            description = "Client-side script and connection detection",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "api_shield",
            product = "Security",
            displayName = "API Shield",
            description = "API discovery, schema validation, mTLS",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "bot_management",
            product = "Security",
            displayName = "Bot Management",
            description = "Bot score insights and mitigation",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "ddos",
            product = "Security",
            displayName = "DDoS Protection",
            description = "Status, events, and mitigations",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "workers",
            product = "Develop",
            displayName = "Workers",
            description = "Deploy, inspect, and manage Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "pages",
            product = "Develop",
            displayName = "Pages",
            description = "Static site and full-stack deployments",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "r2",
            product = "Storage & Media",
            displayName = "R2",
            description = "Object storage buckets",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH
        ),
        Capability(
            id = "kv",
            product = "Develop",
            displayName = "KV",
            description = "Key-value storage for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "d1",
            product = "Develop",
            displayName = "D1",
            description = "Serverless SQL database",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "queues",
            product = "Develop",
            displayName = "Queues",
            description = "Message queues for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "access",
            product = "Zero Trust",
            displayName = "Access",
            description = "Applications, policies, identity providers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH
        ),
        Capability(
            id = "gateway",
            product = "Zero Trust",
            displayName = "Gateway",
            description = "Network, HTTP, and DNS policies",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "tunnels",
            product = "Zero Trust",
            displayName = "Tunnels",
            description = "Cloudflare Tunnel inventory and status",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "device_posture",
            product = "Zero Trust",
            displayName = "Devices & Posture",
            description = "Enrolled devices and posture checks",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1
        ),
        Capability(
            id = "account_members",
            product = "Account",
            displayName = "Members & Roles",
            description = "Account membership, roles, invitations",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            destructiveRisk = DestructiveRisk.HIGH
        ),
        Capability(
            id = "durable_objects",
            product = "Develop",
            displayName = "Durable Objects",
            description = "Stateful Workers coordination",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "workflows",
            product = "Develop",
            displayName = "Workflows",
            description = "Durable multi-step Workers execution",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "hyperdrive",
            product = "Develop",
            displayName = "Hyperdrive",
            description = "Database connection pooling for Workers",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "ai",
            product = "Develop",
            displayName = "Workers AI",
            description = "Run AI models at the edge",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "vectorize",
            product = "Develop",
            displayName = "Vectorize",
            description = "Vector database for AI workloads",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "browser_rendering",
            product = "Develop",
            displayName = "Browser Rendering",
            description = "Headless browser automation",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "stream",
            product = "Storage & Media",
            displayName = "Stream",
            description = "Video hosting and delivery",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "images",
            product = "Storage & Media",
            displayName = "Images",
            description = "Image storage, resizing, delivery",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "email_routing",
            product = "Network",
            displayName = "Email Routing",
            description = "Custom email addresses and routing rules",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "turnstile",
            product = "Security",
            displayName = "Turnstile",
            description = "CAPTCHA alternative widget management",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "spectrum",
            product = "Network",
            displayName = "Spectrum",
            description = "TCP/UDP application proxying",
            scope = CapabilityScope.ZONE,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "magic_network",
            product = "Network",
            displayName = "Magic Firewall / WAN / Transit",
            description = "Network-layer routing and protection",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "logpush",
            product = "Analytics & Logs",
            displayName = "Logpush",
            description = "Bulk log export administration",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P2
        ),
        Capability(
            id = "billing",
            product = "Account",
            displayName = "Billing & Plan",
            description = "Current plan and usage - no payment changes",
            scope = CapabilityScope.ACCOUNT,
            status = CapabilityStatus.NOT_IMPLEMENTED,
            roadmapPhase = RoadmapPhase.P1,
            requiresBrowserHandoff = true
        )
    )

    fun implemented(): List<Capability> = zoneCapabilities.filter { it.status == CapabilityStatus.IMPLEMENTED }

    fun notYetImplemented(): List<Capability> = zoneCapabilities.filter { it.status != CapabilityStatus.IMPLEMENTED }

    fun byId(id: String): Capability? = zoneCapabilities.firstOrNull { it.id == id }
}
