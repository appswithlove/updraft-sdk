pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
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

rootProject.name = "updraft-sdk"
include(":sample:composeApp", ":sample:androidApp", ":updraft-sdk", ":updraft-core", ":updraft-ui-compose")
