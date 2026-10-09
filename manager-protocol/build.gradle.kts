plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.manager.protocol"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
}
dependencies { testImplementation("junit:junit:4.13.2") }
