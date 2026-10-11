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
        // QuickJS (the same build Mihon ships, which extensions call into) is only on JitPack.
        maven("https://www.jitpack.io")
    }
}

rootProject.name = "Yomu"
include(":app")
