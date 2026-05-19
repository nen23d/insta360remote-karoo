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
        // karoo-ext from GitHub Packages (requiere autenticación)
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("USERNAME") ?: ""
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "karoo-insta360"
include(":app")
