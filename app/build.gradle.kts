plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.alexsludds.cocoonra"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.alexsludds.cocoonra"
        minSdk = 26
        targetSdk = 35
        // Each CI run gets its own version (v1.0.<run number>) so updaters like Obtainium see a new release.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.$versionCode"
    }
    // Fixed key committed to the repo so every CI build can be installed over the previous one.
    signingConfigs.getByName("debug") {
        storeFile = rootProject.file("debug.keystore")
        storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
