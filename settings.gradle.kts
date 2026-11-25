pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "KIGTTSNEXT"
include(":app", ":plugin-api", ":material-color", ":plugins:placeholder-tts", ":plugins:placeholder-asr")
include(":plugins:listening-panel")
include(":plugins:voice-input")
include(":plugin-android-api", ":plugin-runtime")
project(":plugin-api").projectDir = file(".sdk/plugin-api")
project(":plugin-android-api").projectDir = file(".sdk/plugin-android-api")
check(file(".sdk/plugin-api/build.gradle.kts").isFile) { "Initialize the pinned SDK: git submodule update --init .sdk" }
include(":manager-protocol", ":plugin-manager")
include(":reading", ":speech-recognition", ":market")
project(":market").projectDir = file(".market")
check(file(".market/build.gradle.kts").isFile) { "Initialize the bundled Extension Market: git submodule update --init .market" }

// Optional cross-repository tests; a normal application build never configures plugin projects.
if (providers.gradleProperty("pluginIntegration").orNull == "true") {
    val sources = file(providers.gradleProperty("pluginSourceRoot").orNull ?: "../KIGTTS-Plugins")
    listOf("cloud-agent", "lua-runtime", "quick-text-tools").forEach { module ->
        val directory = sources.resolve(module)
        check(directory.resolve("build.gradle.kts").isFile) { "Clone KIGTTS/plugin-$module into $directory or set -PpluginSourceRoot" }
        include(":engine-plugins:$module")
        project(":engine-plugins:$module").projectDir = directory
    }
}
