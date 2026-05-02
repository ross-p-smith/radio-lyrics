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
// :omri-usb is the LGPL-2.1 vendored DAB driver at omri-usb/omriusb/.
// The submodule's outer omri-usb/ directory holds its own legacy build.gradle
// that we no longer use; only the inner omriusb/ Android library is included.
include(":omri-usb")
project(":omri-usb").projectDir = file("omri-usb/omriusb")
