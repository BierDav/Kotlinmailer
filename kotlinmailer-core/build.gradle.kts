plugins {
    id("common-build-script")
    id("maven-publish-script")
    kotlin("plugin.serialization")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.2")

    api("org.simplejavamail:batch-module:8.1.0")
    api("org.simplejavamail:simple-java-mail:8.1.0")
    api("org.simplejavamail:smime-module:8.1.0")
}
