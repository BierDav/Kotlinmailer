plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    api(libs.simpleJavaMail)
    api(libs.simpleJavaMail.smime)
}
