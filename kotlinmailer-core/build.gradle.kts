plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.2")

    api("org.simplejavamail:batch-module:7.1.1")
    api("org.simplejavamail:simple-java-mail:7.1.1")
    api("org.simplejavamail:smime-module:7.1.1")
}
