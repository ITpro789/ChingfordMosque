# ---------------------------------------------------------------------------
# ChingfordMosque :app release (R8 full mode, minify + resource shrink).
#
# Most libraries we depend on (OkHttp, Okio, DataStore, kotlinx-coroutines,
# Jetpack Compose, WorkManager) ship their own consumer ProGuard rules via
# their AARs, so we only need to suppress missing-class warnings for optional
# / compile-time-only dependencies that R8 cannot see at runtime.
# ---------------------------------------------------------------------------

# The :core module references java.net.http (JvmHttpFetcher) and org.jsoup,
# neither of which the Android app ever instantiates (we bind OkHttpFetcher
# instead). Suppress missing-class warnings so the minified release build
# does not fail.
-dontwarn java.net.http.**
-dontwarn org.jsoup.**

# OkHttp 4.x has optional compile-time references to these security providers
# (Conscrypt / BouncyCastle / OpenJSSE). They are not bundled with the app, so
# R8 would otherwise warn about the missing classes.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
