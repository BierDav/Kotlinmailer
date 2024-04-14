plugins {
    id("common-build-script")
    id("maven-publish-script")
}

dependencies {
    project(":packages:core")

    implementation(libs.kotlinx.html)
    implementation(libs.simpleJavaMail.simpleJavaMail)
}
