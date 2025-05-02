plugins {
    kotlin("multiplatform")
    id("io.gitlab.arturbosch.detekt")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
}