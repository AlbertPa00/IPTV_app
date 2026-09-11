pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IPTVApp"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:storage")
include(":feature:source")
include(":feature:catalog")
include(":feature:series")
include(":feature:player")
include(":feature:epg")
