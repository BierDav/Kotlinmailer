plugins {
    id("common-build-script")
    id("maven-publish-script")
    alias(libs.plugins.kotlin.serialization)
}

description = "JMAP client for Kotlinmailer"

kotlin {
    allTargets()

    sourceSets {
        commonMain.dependencies {
            api(project(":packages:core"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
