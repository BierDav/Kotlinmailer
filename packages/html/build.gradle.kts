plugins {
    id("common-build-script")
    id("maven-publish-script")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":packages:core"))
            api(libs.kotlinx.html)
        }
        jvmMain.dependencies {
            api(libs.simpleJavaMail.simpleJavaMail)
        }
    }
}
