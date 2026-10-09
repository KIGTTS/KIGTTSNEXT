plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.kigtts.next"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kigtts.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-preview"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("plugin-test-assets"))
}
dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":material-color"))
    implementation(project(":plugin-runtime"))
    implementation(project(":manager-protocol"))
    testImplementation(project(":plugins:placeholder-tts"))
    testImplementation(project(":plugins:placeholder-asr"))
    implementation(project(":plugins:debug-ui"))
    implementation(project(":plugins:listening-panel"))
    implementation(project(":plugins:voice-input"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.51.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.51.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    systemProperty("roborazzi.test.record", "true")
}
val copyPluginTestAssets = tasks.register<Sync>("copyPluginTestAssets") {
    from(rootProject.layout.buildDirectory.dir("plugin-packages")) { include("*-0.5.0.kigplugin") }
    into(layout.buildDirectory.dir("plugin-test-assets"))
}
tasks.matching { it.name.contains("AndroidTest") }.configureEach { dependsOn(copyPluginTestAssets) }
