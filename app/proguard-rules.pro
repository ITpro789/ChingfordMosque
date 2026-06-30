# The :core module references java.net.http (JvmHttpFetcher) and org.jsoup, neither of which
# the Android app ever instantiates (we bind OkHttpFetcher instead). Suppress missing-class
# warnings so a future minified release build does not fail.
-dontwarn java.net.http.**
-dontwarn org.jsoup.**
