plugins {
    id("common-build-script")
    id("maven-publish-script")
}

description = "SMTP client for Kotlinmailer"

kotlin {
    js {
        nodejs()
    }
    appleTargets()
    linuxX64()
    linuxArm64()
    mingwX64()

    applyDefaultHierarchyTemplate {
        common {
            // Targets whose sockets come from ktor-network.
            group("ktor") {
                withJvm()
                group("linux")
                group("mingw")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":packages:core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("ktorMain").dependencies {
            implementation(libs.ktor.network)
            api(libs.ktor.network.tls)
        }
        jvmTest.dependencies {
            implementation(libs.greenmail)
        }
    }
}
