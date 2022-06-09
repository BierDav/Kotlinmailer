plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    api("org.simplejavamail:simple-java-mail:7.1.1")
    api("org.simplejavamail:smime-module:7.1.1")
}
