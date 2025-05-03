
plugins {
    id("common-build-script")
    id("maven-publish-script")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            api(libs.simpleJavaMail.batchModule)
            api(libs.simpleJavaMail.simpleJavaMail)
            api(libs.simpleJavaMail.smimeModule)
        }
    }
}
