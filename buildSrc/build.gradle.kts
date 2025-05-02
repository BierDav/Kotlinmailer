plugins {
  `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    gradleApi()
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.jreleaser.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.binary.compatibility.validator.gradle.plugin)
}

kotlin {
    sourceSets.getByName("main").kotlin.srcDir("buildSrc/src/main/kotlin")
}