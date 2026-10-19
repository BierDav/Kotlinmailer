import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Apple targets, which have native TLS via Foundation. */
fun KotlinMultiplatformExtension.appleTargets() {
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
}

/** Every target the pure Kotlin modules are published for. */
@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.allTargets() {
    jvm()
    js {
        browser { testTask { enabled = false } }
        nodejs()
    }
    wasmJs {
        browser { testTask { enabled = false } }
        nodejs()
    }
    appleTargets()
    linuxX64()
    linuxArm64()
    mingwX64()
}
