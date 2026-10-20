plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.kobweb.gradle.plugin)
    implementation(libs.jreleaser.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.binary.compatibility.validator.gradle.plugin)
}
