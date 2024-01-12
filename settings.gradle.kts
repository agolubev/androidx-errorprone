pluginManagement {
    includeBuild("build-logic")
    repositories {
        maven { url = uri("file:/Users/alexgolubev/src/studio-main/out/repo/") }
        maven {
            url =
                uri("file:/Users/alexgolubev/src/studio-main/prebuilts/tools/common/m2/repository/")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("file:/Users/alexgolubev/src/studio-main/out/repo/") }
        maven {
            url =
                uri("file:/Users/alexgolubev/src/studio-main/prebuilts/tools/common/m2/repository/")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AndroiodX-ErrorProne"
include(":app")
