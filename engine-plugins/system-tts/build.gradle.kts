plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.engines.system"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(project(":engine-plugins:system-support"))
    compileOnly(project(":plugin-android-api"))
    testImplementation(project(":plugin-android-api"))
    testImplementation("junit:junit:4.13.2")
}
tasks.register<Copy>("collectPluginDependencies") {
    dependsOn(":engine-plugins:tts-support:assembleDebug")
    from(project(":engine-plugins:tts-support").layout.buildDirectory.file("outputs/aar/tts-support-debug.aar"))
    dependsOn(":engine-plugins:system-support:assembleDebug")
    from(project(":engine-plugins:system-support").layout.buildDirectory.file("outputs/aar/system-support-debug.aar")); into(layout.buildDirectory.dir("plugin-dependencies"))
}
