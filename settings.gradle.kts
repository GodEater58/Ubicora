pluginManagement {
    repositories { google(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // 4.25: Google's mirror of Maven Central first: Maven Central limits GitHub's build machines (HTTP 429).
    repositories { google(); maven("https://maven-central.storage-download.googleapis.com/maven2/"); mavenCentral() }
}
rootProject.name = "OfficinaStock4Native"
include(":app")
