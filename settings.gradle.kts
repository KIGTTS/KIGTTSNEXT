pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "KIGTTSNEXT"
include(":app", ":plugin-api", ":material-color", ":plugins:placeholder-tts", ":plugins:placeholder-asr")
include(":plugins:debug-ui")
include(":plugins:listening-panel")
include(":plugins:voice-input")
include(":plugin-android-api", ":plugin-runtime", ":engine-plugins:system-tts", ":engine-plugins:sensevoice-asr")
