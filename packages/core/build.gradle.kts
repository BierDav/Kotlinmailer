plugins {
    id("common-build-script")
    id("maven-publish-script")
    id(libs.plugins.kotlin.serialization.get().pluginId) apply false
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.jdk8)

    implementation(libs.simpleJavaMail.batchModule)
    implementation(libs.simpleJavaMail.simpleJavaMail)
    implementation(libs.simpleJavaMail.smimeModule)
}
