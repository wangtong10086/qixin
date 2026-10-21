plugins {
    id("magisk.android.library")
}

android {
    namespace = "io.github.magisk317.uikit.billing"
}

dependencies {
    implementation(project(":magisk-ui-kit"))
    api(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kermit)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}
