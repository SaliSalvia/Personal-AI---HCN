pluginManagement {
  repositories {
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

// CI explicitly provisions JDK 21 and this project has no Java toolchain block.
// Avoiding the optional Foojay settings plugin keeps configuration independent of
// Gradle Plugin Portal availability, so the APK workflow can reach compilation.

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Sali-HCNSEC"

include(":app")
