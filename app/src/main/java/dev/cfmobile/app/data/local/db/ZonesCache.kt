package dev.cfmobile.app.data.local.db

import dev.cfmobile.app.data.remote.dto.CfPlan
import dev.cfmobile.app.data.remote.dto.CfZone

/** A cached zone snapshot plus when it was fetched - the timestamp is what
 *  [dev.cfmobile.app.ui.common.FreshnessLabel] renders for cache-first data, exactly as it
 *  would for a live fetch. */
data class CachedZones(val zones: List<CfZone>, val cachedAt: Long?)

/** Cache-first read-through for the Zones list (PRD §9: offline/cached state) - the app's
 *  single most-viewed screen, so this app-launch-critical path gets a local cache while
 *  every other screen still fetches fresh on each visit. Never a source of truth: on any
 *  doubt, the live API call in ZonesRepository wins and overwrites this. */
class ZonesCache(private val dao: ZoneDao) {

    suspend fun get(accountId: String): CachedZones {
        val rows = dao.getForAccount(accountId)
        return CachedZones(
            zones = rows.map { CfZone(id = it.id, name = it.name, status = it.status, plan = it.planName?.let { name -> CfPlan(name = name) }) },
            cachedAt = rows.maxOfOrNull { it.cachedAt }
        )
    }

    suspend fun save(accountId: String, zones: List<CfZone>, fetchedAt: Long) {
        dao.replaceForAccount(
            accountId,
            zones.map { ZoneEntity(id = it.id, accountId = accountId, name = it.name, status = it.status, planName = it.plan?.name, cachedAt = fetchedAt) }
        )
    }
}
