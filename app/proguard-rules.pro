# R8 rules for the minified release build.
# Most libraries (OkHttp, Okio, Moshi, Tink, coroutines) ship their own consumer
# rules via their AARs; the entries below are defensive safety nets.

# --- Crypto core: used directly, keep intact ---
-keep class com.zkrwatch.data.crypto.** { *; }

# --- Tink (Keystore-backed AEAD) + protobuf-lite it depends on ---
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.protobuf.**

# --- Networking / JSON ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn com.squareup.moshi.**

# Wear Tiles / ProtoLayout occasionally reference optional classes.
-dontwarn androidx.wear.protolayout.**
-dontwarn androidx.wear.tiles.**
