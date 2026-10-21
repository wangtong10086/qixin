plugins {
    id("magisk.android.library")
    id("kotlinx-serialization")
    id(libs.plugins.kotlin.compose.get().pluginId)
}

android {
    namespace = "io.github.magisk317.uikit.update"
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("github") { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        listOf("main", "play").forEach { name ->
            getByName(name).kotlin.directories.add("src/$name/kotlin")
        }
        listOf("github", "fdroid").forEach { flavor ->
            getByName(flavor).kotlin.directories.add("src/$flavor/kotlin")
            getByName(flavor).kotlin.directories.add("src/nonPlay/kotlin")
        }
    }
}

dependencies {
    api(project(":magisk-ui-kit"))
    api(libs.androidx.activity.compose)
    add("playImplementation", libs.play.app.update)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.foundation:foundation:1.13.0-alpha03")
    implementation("androidx.compose.ui:ui:1.13.0-alpha03")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
