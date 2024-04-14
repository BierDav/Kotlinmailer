plugins {
    id("common-build-script")
}

dependencies {
    project(":packages:core")
    project(":packages:html")

    testImplementation("org.slf4j:slf4j-simple:2.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.test {
    useJUnitPlatform()
}
