plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.pulse.workout"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "it.pulse.workout"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "1.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Una chiave permanente resta nei GitHub Actions Secrets, mai nel repository.
    // Il certificato debug preesistente NON può essere riutilizzato senza la sua chiave privata.
    val signingPath = System.getenv("BUTTIGOGYM_SIGNING_FILE")
    val signingReady = !signingPath.isNullOrBlank() &&
        !System.getenv("BUTTIGOGYM_STORE_PASSWORD").isNullOrBlank() &&
        !System.getenv("BUTTIGOGYM_KEY_ALIAS").isNullOrBlank() &&
        !System.getenv("BUTTIGOGYM_KEY_PASSWORD").isNullOrBlank()
    signingConfigs {
        if (signingReady) {
            create("stableRelease") {
                storeFile = file(signingPath!!)
                storePassword = System.getenv("BUTTIGOGYM_STORE_PASSWORD")
                keyAlias = System.getenv("BUTTIGOGYM_KEY_ALIAS")
                keyPassword = System.getenv("BUTTIGOGYM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("stableRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
