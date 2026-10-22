plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization") }
android {
    namespace = "com.kigtts.plugin.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    api(project(":plugin-android-api"))
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootProject.layout.buildDirectory.dir("plugin-packages")) { include("*.kigplugin") })
    inputs.file(rootProject.file("distribution/official-packages.json"))
    systemProperty("kig.plugin.packages", rootProject.layout.buildDirectory.dir("plugin-packages").get().asFile.absolutePath)
}
