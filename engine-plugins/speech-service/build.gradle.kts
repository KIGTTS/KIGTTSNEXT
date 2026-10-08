plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.engines.speechservice"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    compileOnly(project(":plugin-android-api"))
    implementation(project(":engine-plugins:system-support"))
    testImplementation(project(":plugin-android-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.register<Copy>("collectPluginDependencies") {
    dependsOn(":engine-plugins:system-support:assembleDebug")
    from(project(":engine-plugins:system-support").layout.buildDirectory.file("outputs/aar/system-support-debug.aar")); into(layout.buildDirectory.dir("plugin-dependencies"))
}
