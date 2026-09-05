# Tink (pulled in transitively by androidx.security-crypto for EncryptedSharedPreferences)
# references Error Prone's compile-time-only annotations, which aren't shipped as a runtime
# dependency and aren't needed at runtime - safe to tell R8 to stop looking for them.
-dontwarn com.google.errorprone.annotations.**
