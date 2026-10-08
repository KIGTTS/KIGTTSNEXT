plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.engines.systemsupport"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
}
dependencies { compileOnly(project(":plugin-android-api")) }
