import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Enabled in M1 when DI/networking land:
    // alias(libs.plugins.hilt)
    // alias(libs.plugins.ksp)
}

// --- Build-time secrets (see keys.properties.template) ---
// Region-scoped Zkr app keys, kept out of source control. Missing file => empty
// strings so the project still builds; the app surfaces a clear "keys not set" error.
val keysFile = rootProject.file("keys.properties")
val keyProps = Properties().apply {
    if (keysFile.exists()) keysFile.inputStream().use { load(it) }
}
fun key(name: String): String = keyProps.getProperty(name, "")

android {
    namespace = "com.zkrwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zkrwatch"
        minSdk = 30          // Wear OS 3+; covers Galaxy Watch 4 and up
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        // Injected at build time from keys.properties. BuildConfig.* at runtime.
        buildConfigField("String", "HMAC_ACCESS_KEY", "\"${key("HMAC_ACCESS_KEY")}\"")
        buildConfigField("String", "HMAC_SECRET_KEY", "\"${key("HMAC_SECRET_KEY")}\"")
        buildConfigField("String", "PASSWORD_PUBLIC_KEY", "\"${key("PASSWORD_PUBLIC_KEY")}\"")
        buildConfigField("String", "PROD_SECRET", "\"${key("PROD_SECRET")}\"")
        buildConfigField("String", "VIN_KEY", "\"${key("VIN_KEY")}\"")
        buildConfigField("String", "VIN_IV", "\"${key("VIN_IV")}\"")
        buildConfigField("String", "COUNTRY_CODE", "\"${keyProps.getProperty("COUNTRY_CODE", "AU")}\"")
        // Optional: bake account credentials for a personal sideload (M5 replaces
        // this with on-watch secure entry). Blank => app shows a "not configured" screen.
        buildConfigField("String", "ACCOUNT_EMAIL", "\"${key("ACCOUNT_EMAIL")}\"")
        buildConfigField("String", "ACCOUNT_PASSWORD", "\"${key("ACCOUNT_PASSWORD")}\"")
    }

    signingConfigs {
        create("sideload") {
            // Personal sideload: sign the release with the debug keystore so
            // `assembleRelease` produces an installable APK (not for Play Store).
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Lighter APK: strip unused code + resources via R8.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("sideload")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// --- Release signing consistency guard ---------------------------------------
// In-app self-update only works if every release is signed with the SAME key —
// Android rejects an update whose signature differs. This pins the release
// signing certificate; a release build FAILS if a different keystore/key is used.
// If ~/.android/debug.keystore is ever lost, self-update breaks for all existing
// users, so BACK IT UP. See RELEASING.md.
val expectedReleaseCertSha256 =
    "E8:9F:4F:29:28:96:4E:81:8F:B2:75:26:27:D5:9E:0A:97:A8:80:25:94:EA:94:2E:E1:4B:B1:C4:5E:9E:44:BE"

val verifyReleaseSigningCert = tasks.register("verifyReleaseSigningCert") {
    description = "Fails the release build if the signing key differs from the pinned cert (needed for self-update)."
    doLast {
        val cfg = android.signingConfigs.getByName("sideload")
        val ksFile = cfg.storeFile ?: throw GradleException("sideload signingConfig has no storeFile")
        if (!ksFile.exists()) throw GradleException("Signing keystore not found: $ksFile — see RELEASING.md.")
        val pass = (cfg.storePassword ?: "").toCharArray()
        var ks: KeyStore? = null
        for (type in listOf("PKCS12", "JKS")) {
            try {
                val candidate = KeyStore.getInstance(type)
                ksFile.inputStream().use { candidate.load(it, pass) }
                ks = candidate
                break
            } catch (_: Exception) {
            }
        }
        if (ks == null) throw GradleException("Could not read keystore $ksFile (tried PKCS12/JKS).")
        val cert = ks.getCertificate(cfg.keyAlias) as X509Certificate
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded).joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        if (!actual.equals(expectedReleaseCertSha256, ignoreCase = true)) {
            throw GradleException(
                "\n  Release signing certificate MISMATCH — self-update would break for existing users.\n" +
                    "    expected: $expectedReleaseCertSha256\n" +
                    "    actual:   $actual\n" +
                    "  Use the original keystore (see RELEASING.md) before cutting a release.\n",
            )
        }
        logger.lifecycle("Release signing certificate OK ($actual)")
    }
}

// Verify the signing key before any release APK is packaged.
tasks.matching { it.name == "packageRelease" }.configureEach {
    dependsOn(verifyReleaseSigningCert)
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    debugImplementation(libs.wear.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Networking (M1)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)

    // Wear system integrations (M4)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation(libs.protolayout.expression)
    implementation(libs.guava)

    // Background command execution from the Tile (lock/unlock without opening the app)
    implementation(libs.work.runtime.ktx)

    // Secure session storage (M5)
    implementation(libs.tink.android)

    // --- Enabled in later milestones ---
    // implementation(libs.hilt.android); ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
