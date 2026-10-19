plugins {
    id("common-build-script")
    id("maven-publish-script")
}

description = "kotlinx.html integration for Kotlinmailer"

kotlin {
    allTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":packages:core"))
            api(libs.kotlinx.html)
        }
    }
}
