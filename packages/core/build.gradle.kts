plugins {
    id("common-build-script")
    id("maven-publish-script")
}

description = "Kotlinmailer email model, builder DSL and MIME renderer"

kotlin {
    allTargets()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
