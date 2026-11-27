plugins { id("com.android.application");kotlin("android");id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="com.kigtts.manager"
    compileSdk=36
    defaultConfig { applicationId="com.kigtts.manager";minSdk=26;targetSdk=36;versionCode=10000;versionName="1.0.0" }
    buildFeatures { compose=true;buildConfig=true }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
}
dependencies {
    implementation(project(":manager-protocol"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
