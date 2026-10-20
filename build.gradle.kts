plugins {
    id(libs.plugins.dokka.get().pluginId)
    id(libs.plugins.binary.compatibility.validator.get().pluginId)
}

repositories {
    mavenCentral()
}

apiValidation {
    ignoredProjects += "demo"
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

dependencies {
    listOf("core", "smtp", "jmap", "html").forEach { dokka(project(":packages:$it")) }
}

dokka {
    moduleName.set("Kotlinmailer")
}
