plugins {
    id("common-build-script")
    id("maven-publish-script")
    kotlin("plugin.serialization")
}

val simpleJavaMailVersion = "8.8.2"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.0")

    implementation("org.simplejavamail:batch-module:$simpleJavaMailVersion")
    implementation("org.simplejavamail:simple-java-mail:$simpleJavaMailVersion")
    implementation("org.simplejavamail:smime-module:$simpleJavaMailVersion")
}
