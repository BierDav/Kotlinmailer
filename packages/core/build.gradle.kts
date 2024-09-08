plugins {
    id("common-build-script")
    id("maven-publish-script")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)

    api(libs.simpleJavaMail.batchModule)
    api(libs.simpleJavaMail.simpleJavaMail)
    api(libs.simpleJavaMail.smimeModule)
}


