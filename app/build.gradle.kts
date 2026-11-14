plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.officinastock.app"
    compileSdk = 36
    defaultConfig {
        // ID distinto: installazione indipendente dalla vecchia app. Per trasferire i dati usare backup cifrato.
        // namespace Kotlin storico invariato: NON determina l'identita del pacchetto Android.
        applicationId = "com.ubicora.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4202
        versionName = "4.20.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // No ephemeral release keys: updates require exactly the same signing certificate.
    // CI builds an unsigned release until all four secure variables are supplied.
    val releaseStore = System.getenv("UBICORA_RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("UBICORA_RELEASE_STORE_PASSWORD")
    val releaseAlias = System.getenv("UBICORA_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("UBICORA_RELEASE_KEY_PASSWORD")
    val signingValues = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
    require(signingValues.all { it.isNullOrEmpty() } || signingValues.all { !it.isNullOrEmpty() }) {
        "Release signing incomplete: supply all four UBICORA_RELEASE_* environment variables"
    }
    if(signingValues.all { !it.isNullOrEmpty() }) {
        signingConfigs.create("ubicoraStable") {
            storeFile = file(requireNotNull(releaseStore))
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if(signingConfigs.findByName("ubicoraStable") != null) {
                signingConfig = signingConfigs.getByName("ubicoraStable")
            }
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("com.google.ar:core:1.56.0")
    // 4.9: phone-to-phone network without router (Bluetooth + Wi-Fi Direct) for team sync.
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    // 4.12: archive encrypted on the phone (AES-256), key sealed by the Android Keystore.
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // Screenshots of the real screens for the README and the guide (ReadmeScreenshotsTest).
    testImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

