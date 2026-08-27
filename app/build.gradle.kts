import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * The API origin is machine-supplied and never committed.
 *
 * Set `narratrace.apiBaseUrl=https://…` in local.properties (already gitignored),
 * or supply NARRATRACE_API_BASE_URL in CI. An absent value leaves the build failing
 * closed at the first request, which is the intended state for a checked-out tree.
 */
val apiBaseUrl: String = run {
    val fromEnv = System.getenv("NARRATRACE_API_BASE_URL").orEmpty()
    if (fromEnv.isNotBlank()) return@run fromEnv
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) return@run ""
    Properties()
        .apply { localProperties.inputStream().use(::load) }
        .getProperty("narratrace.apiBaseUrl")
        .orEmpty()
}

/**
 * The Google Web client ID that Credential Manager presents as `serverClientId`.
 *
 * Not a secret — it is the public audience identifier that appears in the `aud`
 * claim of every ID token, and the backend matches it against
 * GOOGLE_MOBILE_CLIENT_IDS. It is kept out of the repository anyway so debug,
 * release, and CI cannot silently diverge in a file someone has to remember to edit.
 *
 * Note this is the *Web* client ID, not the Android one. The Android client exists
 * so Google will issue a credential to this package and signing certificate; it
 * never appears in the token audience.
 */
val googleServerClientId: String = run {
    val fromEnv = System.getenv("NARRATRACE_GOOGLE_SERVER_CLIENT_ID").orEmpty()
    if (fromEnv.isNotBlank()) return@run fromEnv
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) return@run ""
    Properties()
        .apply { localProperties.inputStream().use(::load) }
        .getProperty("narratrace.googleServerClientId")
        .orEmpty()
}
fun configured(name: String, property: String): String = System.getenv(name).orEmpty().ifBlank {
    val file = rootProject.file("local.properties"); if (!file.exists()) "" else Properties().apply { file.inputStream().use(::load) }.getProperty(property).orEmpty()
}
val firebaseApiKey = configured("NARRATRACE_FIREBASE_API_KEY", "narratrace.firebaseApiKey")
val firebaseApplicationId = configured("NARRATRACE_FIREBASE_APPLICATION_ID", "narratrace.firebaseApplicationId")
val firebaseProjectId = configured("NARRATRACE_FIREBASE_PROJECT_ID", "narratrace.firebaseProjectId")
val firebaseSenderId = configured("NARRATRACE_FIREBASE_SENDER_ID", "narratrace.firebaseSenderId")
val releaseKeystorePath = System.getenv("NARRATRACE_ANDROID_KEYSTORE_PATH").orEmpty()
val releaseKeystorePassword = System.getenv("NARRATRACE_ANDROID_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("NARRATRACE_ANDROID_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("NARRATRACE_ANDROID_KEY_PASSWORD").orEmpty()
val releaseVersionCode = System.getenv("NARRATRACE_ANDROID_VERSION_CODE").orEmpty().toIntOrNull() ?: 1
val releaseVersionName = System.getenv("NARRATRACE_ANDROID_VERSION_NAME").orEmpty().ifBlank { "1.0.0" }
val hasReleaseSigning = listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all(String::isNotBlank)

android {
    namespace = "io.narratrace.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.narratrace.android"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"$firebaseApplicationId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
    }

    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            // A release build must never ship pointed at a non-production or
            // cleartext origin. Fail the build rather than discover it in the store.
            check(apiBaseUrl.isBlank() || apiBaseUrl.startsWith("https://")) {
                "NARRATRACE_API_BASE_URL must be an https:// origin for release builds."
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // `kotlinOptions` is deprecated in Kotlin 2.x. The compilerOptions DSL below is
    // the replacement; the JVM target must stay 17 to match sourceCompatibility above.

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
    )
}

tasks.register("verifyStoreRelease") {
    group = "verification"
    description = "Fails unless every Doppler-provided production and signing input is present."
    inputs.property("apiBaseUrl", apiBaseUrl)
    inputs.property("googleServerClientId", googleServerClientId)
    inputs.property("firebaseApiKeyPresent", firebaseApiKey.isNotBlank())
    inputs.property("firebaseApplicationIdPresent", firebaseApplicationId.isNotBlank())
    inputs.property("firebaseProjectIdPresent", firebaseProjectId.isNotBlank())
    inputs.property("firebaseSenderIdPresent", firebaseSenderId.isNotBlank())
    inputs.property("releaseKeystorePath", releaseKeystorePath)
    inputs.property("releaseKeystorePasswordPresent", releaseKeystorePassword.isNotBlank())
    inputs.property("releaseKeyAliasPresent", releaseKeyAlias.isNotBlank())
    inputs.property("releaseKeyPasswordPresent", releaseKeyPassword.isNotBlank())
    inputs.property("releaseVersionCode", releaseVersionCode)
    inputs.property("releaseVersionName", releaseVersionName)
    doLast {
        val configured = inputs.properties
        check((configured["apiBaseUrl"] as String).startsWith("https://")) { "NARRATRACE_API_BASE_URL must be a production HTTPS origin." }
        check((configured["googleServerClientId"] as String).isNotBlank()) { "NARRATRACE_GOOGLE_SERVER_CLIENT_ID is required." }
        check(listOf("firebaseApiKeyPresent", "firebaseApplicationIdPresent", "firebaseProjectIdPresent", "firebaseSenderIdPresent").all { configured[it] == true }) { "All NARRATRACE_FIREBASE_* values are required." }
        check(listOf("releaseKeystorePasswordPresent", "releaseKeyAliasPresent", "releaseKeyPasswordPresent").all { configured[it] == true } && (configured["releaseKeystorePath"] as String).isNotBlank()) { "All NARRATRACE_ANDROID_KEY* signing values are required." }
        check(File(configured["releaseKeystorePath"] as String).isFile) { "The configured Android keystore file does not exist." }
        check((configured["releaseVersionCode"] as Int) > 0 && (configured["releaseVersionName"] as String).isNotBlank()) { "A positive version code and version name are required." }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Credential Manager is the supported route to a Google ID token on Android.
    // The legacy Google Sign-In SDK is deprecated, and the browser-redirect
    // authorization-code flow iOS uses is unavailable here: Android OAuth clients
    // are bound to package name and signing certificate and are never issued the
    // reversed-client-id URL scheme that flow depends on.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
