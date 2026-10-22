plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.wangtong10086.qixin"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.wangtong10086.qixin"
        minSdk = 36
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.0-alpha5"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true; aidl = true }
    // Compile the pinned runtime contract directly so both APKs use the same wire types.
    sourceSets.getByName("main") {
        java.srcDirs("../runtime/upstream/manager/contract/src/main/java",
            "../runtime/upstream/core/src/commonMain/kotlin/io/github/magisk317/mipush/manager")
        aidl.srcDir("../runtime/upstream/manager/contract/src/main/aidl")
    }
    packaging { jniLibs.useLegacyPackaging = true; resources { merges += "META-INF/xposed/*" } }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    compileOnly(files("../vendor/api-102.jar"))
}
