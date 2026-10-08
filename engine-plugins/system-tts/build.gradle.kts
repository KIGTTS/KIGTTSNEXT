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
    compileOnly(project(":plugin-android-api"))
    testImplementation(project(":plugin-android-api"))
    testImplementation("junit:junit:4.13.2")
}
