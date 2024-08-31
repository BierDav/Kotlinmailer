import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id(libs.plugins.dokka.get().pluginId)
    id(libs.plugins.detekt.get().pluginId)
    id(libs.plugins.binary.compatibility.validator.get().pluginId)
}

repositories {
    mavenCentral()
}

detekt {
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "1.8"
}

tasks.dokkaHtmlMultiModule {
    moduleName.set("Kotlinmailer")
    includes.from(File("docs").listFiles().filter { it.isFile })
}