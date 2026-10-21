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
include(":plugin-android-api", ":plugin-runtime", ":engine-plugins:sensevoice-asr")
include(":engine-plugins:piper-tts", ":engine-plugins:speech-service")
include(":engine-plugins:streaming-asr")
include(":engine-plugins:recognition-service")
include(":engine-plugins:quick-text-tools")
include(":engine-plugins:tts-support")
include(":manager-protocol", ":plugin-manager")
include(":engine-plugins:cloud-agent", ":engine-plugins:lua-runtime")
include(":engine-plugins:package-store")
include(":engine-plugins:native-builder")
include(":engine-plugins:ui-debug")
