pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "radio-lyric"
include(":app")
// TODO(Phase 2 - Step 2.1): include(":omri-usb") once the Git submodule at omri-usb/
// is added and ported (see .copilot-tracking/details/2026-05-01/dab-radio-lyrics-app-details.md
// Steps 2.1-2.2). Commented out so `./gradlew help` succeeds before the submodule lands.
// include(":omri-usb")
