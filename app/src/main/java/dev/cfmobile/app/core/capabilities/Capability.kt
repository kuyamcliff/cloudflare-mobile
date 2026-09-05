package dev.cfmobile.app.core.capabilities

/** Whether a capability's code actually exists in this app yet. Never render a control for
 *  something that isn't [IMPLEMENTED] as if it were (PRD §4.4, §115). */
enum class CapabilityStatus {
    IMPLEMENTED,
    NOT_IMPLEMENTED,
    /** Cloudflare requires a browser/dashboard-only workflow this app cannot safely replicate
     *  from the device - see PRD §115's "LIMITATION — EXTERNAL PLATFORM REQUIREMENT" rule. */
    LIMITATION_EXTERNAL_PLATFORM
}

/** Which Cloudflare Product Coverage Roadmap phase a capability belongs to (PRD §80). This is
 *  a priority label, not a status - a P0 item can still be [CapabilityStatus.NOT_IMPLEMENTED]
 *  today. */
enum class RoadmapPhase { P0, P1, P2 }

enum class DestructiveRisk { NONE, LOW, MEDIUM, HIGH, CRITICAL }

enum class CapabilityScope { USER, ACCOUNT, ZONE }

/**
 * One entry in the Cloudflare Capability Registry (PRD §33). The zone menu and other
 * navigation surfaces render from this list instead of a hard-coded feature set, so adding
 * Cloudflare product coverage means adding a row here plus its screen - not restructuring
 * navigation.
 */
data class Capability(
    val id: String,
    val product: String,
    val displayName: String,
    val description: String,
    val scope: CapabilityScope,
    val status: CapabilityStatus,
    val roadmapPhase: RoadmapPhase,
    val destructiveRisk: DestructiveRisk = DestructiveRisk.NONE,
    val requiresBrowserHandoff: Boolean = false,
    /** Only set for [CapabilityStatus.IMPLEMENTED] zone-scoped capabilities with a real screen.
     *  Takes the zone name too (not just its id) so every zone-scoped screen can name its
     *  target in destructive confirmations (PRD §49, §93). */
    val zoneRoute: ((zoneId: String, zoneName: String) -> String)? = null,
    /** True for capabilities Cloudflare itself considers legacy (PRD §18, §79). */
    val deprecated: Boolean = false,
    val migrationHint: String? = null
)
