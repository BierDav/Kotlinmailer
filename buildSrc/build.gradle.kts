plugins {
  `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    gradleApi()
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.gradle.maven.publish.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.binary.compatibility.validator.gradle.plugin)
}
