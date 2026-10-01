# Tink (pulled in transitively by androidx.security-crypto for EncryptedSharedPreferences)
# references Error Prone's compile-time-only annotations, which aren't shipped as a runtime
# dependency and aren't needed at runtime - safe to tell R8 to stop looking for them.
-dontwarn com.google.errorprone.annotations.**

# JSON models. Most have Moshi codegen adapters (whose rules ship with Moshi), but a few are
# read reflectively through KotlinJsonAdapterFactory, which needs their Kotlin metadata and
# property names intact. Keeping the model packages whole is cheap and removes the risk of a
# release build parsing Cloudflare responses differently from debug.
-keep class kotlin.Metadata { *; }
-keep class dev.cfmobile.app.data.remote.dto.** { *; }
-keep class dev.cfmobile.app.data.local.AccountMetadata { *; }
-keep class dev.cfmobile.app.data.local.LegacyTokenStoreMigration$LegacySavedToken { *; }
-keep class dev.cfmobile.app.core.capabilities.CapabilityRepository$CachedCapabilities { *; }
-keep class dev.cfmobile.app.data.remote.ErrorEnvelope { *; }
-keep class dev.cfmobile.app.data.remote.GraphQl* { *; }
-keep class dev.cfmobile.app.data.repository.ScreenshotErrorEnvelope { *; }

# WorkManager instantiates workers by class name.
-keep class dev.cfmobile.app.core.transfers.TransferWorker { <init>(...); }

# Strip debug and verbose logging calls from release builds entirely.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
