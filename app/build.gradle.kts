plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.officinastock.app"
    compileSdk = 35
    defaultConfig {
        // ID distinto: installazione indipendente dalla vecchia app. Per trasferire i dati usare backup cifrato.
        // namespace Kotlin storico invariato: NON determina l'identita del pacchetto Android.
        applicationId = "com.ubicora.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 416
        versionName = "4.1.6-ubicora-identity"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
