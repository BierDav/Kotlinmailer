plugins {
  `kotlin-dsl`
}

repositories {
    mavenCentral()
}

val kotlinVersion = "1.8.20"

dependencies {
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
}
