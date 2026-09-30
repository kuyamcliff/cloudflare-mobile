package dev.cfmobile.app.ui.graphql

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class TimeRange(val label: String, val minutes: Long) {
    H1("1h", 60), H6("6h", 360), H24("24h", 1440), D7("7d", 10080), D30("30d", 43200)
}

enum class TemplateScope { ZONE, ACCOUNT }

/**
 * Starting points for Cloudflare's GraphQL Analytics API. Each is a single query with
 * explicit limits and a bounded time filter (spec 254, 255). [maxRange] reflects the dataset's
 * own lookback: adaptive datasets are not queried beyond what Cloudflare retains.
 */
data class GraphQlTemplate(
    val id: String,
    val title: String,
    val scope: TemplateScope,
    val query: String,
    val usesDates: Boolean,
    val maxRange: TimeRange
) {
    fun variables(tag: String, range: TimeRange, now: Instant = Instant.now()): String {
        val effective = if (range.minutes > maxRange.minutes) maxRange else range
        val start = now.minus(effective.minutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS)
        val end = now.truncatedTo(ChronoUnit.SECONDS)
        val tagName = if (scope == TemplateScope.ZONE) "zoneTag" else "accountTag"
        return if (usesDates) {
            val s = LocalDate.ofInstant(start, ZoneOffset.UTC)
            val e = LocalDate.ofInstant(end, ZoneOffset.UTC)
            "{\n  \"$tagName\": \"$tag\",\n  \"since\": \"$s\",\n  \"until\": \"$e\"\n}"
        } else {
            "{\n  \"$tagName\": \"$tag\",\n  \"since\": \"$start\",\n  \"until\": \"$end\"\n}"
        }
    }
}

object GraphQlTemplates {
    val all = listOf(
        GraphQlTemplate(
            "zone-traffic", "Zone traffic by day", TemplateScope.ZONE,
            """
            query ZoneTraffic(${'$'}zoneTag: string!, ${'$'}since: Date!, ${'$'}until: Date!) {
              viewer {
                zones(filter: { zoneTag: ${'$'}zoneTag }) {
                  httpRequests1dGroups(limit: 31, filter: { date_geq: ${'$'}since, date_leq: ${'$'}until }, orderBy: [date_ASC]) {
                    dimensions { date }
                    sum { requests cachedRequests bytes cachedBytes threats pageViews }
                    uniq { uniques }
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = true, maxRange = TimeRange.D30
        ),
        GraphQlTemplate(
            "zone-status", "Requests by status code", TemplateScope.ZONE,
            """
            query ZoneStatus(${'$'}zoneTag: string!, ${'$'}since: Time!, ${'$'}until: Time!) {
              viewer {
                zones(filter: { zoneTag: ${'$'}zoneTag }) {
                  httpRequestsAdaptiveGroups(limit: 20, filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }, orderBy: [count_DESC]) {
                    count
                    dimensions { edgeResponseStatus }
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = false, maxRange = TimeRange.D7
        ),
        GraphQlTemplate(
            "zone-security", "Security events", TemplateScope.ZONE,
            """
            query SecurityEvents(${'$'}zoneTag: string!, ${'$'}since: Time!, ${'$'}until: Time!) {
              viewer {
                zones(filter: { zoneTag: ${'$'}zoneTag }) {
                  firewallEventsAdaptive(limit: 50, filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }, orderBy: [datetime_DESC]) {
                    datetime
                    action
                    source
                    clientIP
                    clientCountryName
                    clientRequestHTTPHost
                    clientRequestPath
                    ruleId
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = false, maxRange = TimeRange.H24
        ),
        GraphQlTemplate(
            "workers", "Workers invocations", TemplateScope.ACCOUNT,
            """
            query Workers(${'$'}accountTag: string!, ${'$'}since: Time!, ${'$'}until: Time!) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  workersInvocationsAdaptive(limit: 50, filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }, orderBy: [sum_requests_DESC]) {
                    dimensions { scriptName status }
                    sum { requests errors subrequests }
                    quantiles { cpuTimeP50 cpuTimeP99 }
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = false, maxRange = TimeRange.D7
        ),
        GraphQlTemplate(
            "r2-storage", "R2 storage by bucket", TemplateScope.ACCOUNT,
            """
            query R2Storage(${'$'}accountTag: string!, ${'$'}since: Time!, ${'$'}until: Time!) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  r2StorageAdaptiveGroups(limit: 100, filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }) {
                    dimensions { bucketName }
                    max { objectCount payloadSize metadataSize }
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = false, maxRange = TimeRange.D30
        ),
        GraphQlTemplate(
            "r2-operations", "R2 operations", TemplateScope.ACCOUNT,
            """
            query R2Operations(${'$'}accountTag: string!, ${'$'}since: Time!, ${'$'}until: Time!) {
              viewer {
                accounts(filter: { accountTag: ${'$'}accountTag }) {
                  r2OperationsAdaptiveGroups(limit: 100, filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }) {
                    dimensions { bucketName actionType }
                    sum { requests }
                  }
                }
              }
            }
            """.trimIndent(),
            usesDates = false, maxRange = TimeRange.D30
        )
    )
}

/** Cheap local checks before a query costs any of the 300-per-5-minutes budget (spec 48). */
object GraphQlValidator {
    fun validate(query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return "Enter a query"
        var depth = 0
        var inString = false
        var prev = ' '
        for (c in q) {
            if (c == '"' && prev != '\\') inString = !inString
            if (!inString) {
                if (c == '{') depth++
                if (c == '}') depth--
                if (depth < 0) return "Unbalanced braces"
            }
            prev = c
        }
        if (inString) return "Unterminated string"
        if (depth != 0) return "Unbalanced braces"
        if (Regex("\\bmutation\\b").containsMatchIn(q.substringBefore('{'))) return "Mutations are not part of the Analytics API"
        return null
    }
}
