import com.android.build.api.instrumentation.*
import org.objectweb.asm.*

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
/** Foundation 1.9.4 has no public per-field magnifier option. Keep its selection/input
 * implementation and wrap only its magnifier modifier with a scoped host policy. */
abstract class SubtitleMagnifierFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData) =
        classData.className == "androidx.compose.foundation.text.selection.TextFieldSelectionManager_androidKt"

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            private var matched = false
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val visitor=super.visitMethod(access,name,descriptor,signature,exceptions)
                if(name!="textFieldMagnifier" || descriptor!="(Landroidx/compose/ui/Modifier;Landroidx/compose/foundation/text/selection/TextFieldSelectionManager;)Landroidx/compose/ui/Modifier;")return visitor
                matched=true
                return object : MethodVisitor(Opcodes.ASM9,visitor) {
                    override fun visitInsn(opcode: Int) {
                        if(opcode==Opcodes.ARETURN) {
                            super.visitVarInsn(Opcodes.ALOAD,0)
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/kigtts/next/ui/home/SubtitleMagnifierPolicyKt",
                                "withSubtitleMagnifierPolicy","(Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;",false)
                        }
                        super.visitInsn(opcode)
                    }
                }
            }
            override fun visitEnd() {
                check(matched) { "Foundation magnifier bridge changed; update the scoped policy before building." }
                super.visitEnd()
            }
        }
}

androidComponents.onVariants {
    it.instrumentation.transformClassesWith(SubtitleMagnifierFactory::class.java,InstrumentationScope.ALL) {}
    it.instrumentation.transformClassesWith(InputCursorThicknessFactory::class.java,InstrumentationScope.ALL) {}
    it.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
}

/** Foundation 1.9.4 shares this native cursor metric between value- and state-based fields. */
abstract class InputCursorThicknessFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData) =
        classData.className == "androidx.compose.foundation.text.TextFieldCursor_androidKt"
    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            private var matched = false
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (name != "getDefaultCursorThickness" || descriptor != "()F") return visitor
                matched = true
                visitor.visitCode(); visitor.visitLdcInsn(3f); visitor.visitInsn(Opcodes.FRETURN)
                visitor.visitMaxs(1, 0); visitor.visitEnd()
                return null
            }
            override fun visitEnd() {
                check(matched) { "Foundation cursor metric changed; update the 3dp cursor bridge before building." }
                super.visitEnd()
            }
        }
}

android {
    namespace = "com.kigtts.next"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kigtts.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("plugin-test-assets"))
    sourceSets.getByName("androidTest").manifest.srcFile("src/test/AndroidManifest.xml")
    if (providers.gradleProperty("pluginIntegration").orNull == "true") {
        sourceSets.getByName("test").java.srcDir("src/pluginIntegrationTest/java")
    }
    if (providers.gradleProperty("pluginArtwork").orNull == "true") {
        sourceSets.getByName("test").java.srcDir("src/pluginArtworkTest/java")
    }
}
dependencies {
    implementation(project(":market"))
    implementation(files(
        rootProject.file("third-party/kigarface/fivepoint-android-1.1.8.aar"),
        rootProject.file("third-party/kigarface/opencv-rgb-arm64-4.12.0.aar"),
        rootProject.file("third-party/kigarface/ort-fivepoint-arm64-1.24.3.aar"),
    ))
    implementation(project(":reading"))
    implementation(project(":speech-recognition"))
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation(project(":plugin-api"))
    implementation(project(":material-color"))
    implementation(project(":plugin-runtime"))
    implementation(project(":manager-protocol"))
    testImplementation(project(":plugins:placeholder-tts"))
    testImplementation(project(":plugins:placeholder-asr"))
    if (providers.gradleProperty("pluginIntegration").orNull == "true") {
        testImplementation(project(":engine-plugins:quick-text-tools"))
        testImplementation(project(":engine-plugins:cloud-agent"))
        testImplementation(project(":engine-plugins:lua-runtime"))
    }
    testImplementation(project(":plugins:listening-panel"))
    testImplementation(project(":plugins:voice-input"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    // The scoped magnifier build bridge is checked against this Foundation implementation.
    implementation("androidx.compose.foundation:foundation:1.9.4") { version { strictly("1.9.4") } }
    implementation("androidx.compose.material3:material3:1.5.0-alpha01")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-video:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-extensions:$cameraX")
    implementation("androidx.heifwriter:heifwriter:1.1.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.24.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.24.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
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
    val nativeTemp = rootProject.layout.projectDirectory.dir(".tools/test-tmp").asFile
    systemProperty("java.io.tmpdir", nativeTemp.absolutePath)
    doFirst { nativeTemp.mkdirs() }
}
val copyPluginTestAssets = tasks.register<Sync>("copyPluginTestAssets") {
    from(rootProject.layout.buildDirectory.dir("plugin-packages")) { include("*-0.8.0.kigplugin") }
    into(layout.buildDirectory.dir("plugin-test-assets"))
}
if (providers.gradleProperty("pluginIntegration").orNull == "true") {
    tasks.withType<Test>().configureEach {
        val sources = rootProject.file(providers.gradleProperty("pluginSourceRoot").orNull ?: "../KIGTTS-Plugins")
        systemProperty("kig.quick.example.assets", sources.resolve("quick-text-tools/src/main/assets").absolutePath)
    }
}
tasks.matching { it.name.contains("AndroidTest") }.configureEach { dependsOn(copyPluginTestAssets) }
