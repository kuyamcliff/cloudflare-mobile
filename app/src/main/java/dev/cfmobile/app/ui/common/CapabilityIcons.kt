package dev.cfmobile.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ScatterPlot
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cfmobile.app.core.capabilities.Capability

/**
 * Maps a [Capability] to its menu icon. This lives in the UI layer on purpose: the registry in
 * core/capabilities stays free of Compose types, so it can be unit-tested (and eventually
 * reused on another surface) without dragging a UI toolkit along.
 *
 * Unknown ids fall back to a generic icon rather than failing - adding a registry entry should
 * never require touching this file first.
 */
fun capabilityIcon(capability: Capability): ImageVector = when (capability.id) {
    "dns.records" -> Icons.Filled.Dns
    "ssl.tls" -> Icons.Filled.Lock
    "firewall.legacy", "waf.rulesets", "rate_limiting", "bot_management", "ddos", "page_shield" -> Icons.Filled.Shield
    "security_events" -> Icons.Filled.Warning
    "api_shield" -> Icons.Filled.Api
    "page_rules", "transform_rules" -> Icons.AutoMirrored.Filled.Rule
    "caching" -> Icons.Filled.Http
    "analytics" -> Icons.Filled.Analytics
    "account_members" -> Icons.Filled.People
    "audit_logs" -> Icons.Filled.History
    "load_balancing" -> Icons.Filled.CallSplit
    "workers", "hyperdrive" -> Icons.Filled.Bolt
    "pages" -> Icons.Filled.Language
    "r2" -> Icons.Filled.Inventory2
    "kv" -> Icons.Filled.Key
    "d1", "durable_objects" -> Icons.Filled.Storage
    "queues" -> Icons.Filled.Queue
    "workflows" -> Icons.Filled.AccountTree
    "ai" -> Icons.Filled.AutoAwesome
    "vectorize" -> Icons.Filled.ScatterPlot
    "browser_rendering" -> Icons.Filled.PhotoCamera
    "stream" -> Icons.Filled.Videocam
    "images" -> Icons.Filled.Image
    "email_routing" -> Icons.Filled.Email
    "turnstile" -> Icons.Filled.VerifiedUser
    "spectrum" -> Icons.Filled.SettingsEthernet
    "magic_network" -> Icons.Filled.Hub
    "logpush" -> Icons.Filled.Upload
    "billing" -> Icons.Filled.CreditCard
    "access" -> Icons.Filled.Shield
    "gateway" -> Icons.Filled.Dns
    "tunnels" -> Icons.Filled.Router
    "device_posture" -> Icons.Filled.Devices
    else -> Icons.Filled.Extension
}
