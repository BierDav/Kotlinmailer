plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    api(project(":${rootProject.name}-core"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.2")
    api("org.simplejavamail:batch-module:7.1.1")
}
