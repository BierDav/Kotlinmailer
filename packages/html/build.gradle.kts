plugins {
    id("common-build-script")
    id("maven-publish-script")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            project(":packages:core")

            api(libs.kotlinx.html)
            api(libs.simpleJavaMail.simpleJavaMail)
        }
    }
}
