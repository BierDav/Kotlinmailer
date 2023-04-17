plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    api(project(":${rootProject.name}-core"))

    api("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
}
