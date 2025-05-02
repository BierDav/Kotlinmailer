import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jreleaser.model.Signing
import java.net.URI

val githubProject = "BierDav/Kotinmailer"

description = "A simple coroutine based Kotlin Email API for client projects"

plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jreleaser")
}

// Configure Java publication
java {
    withJavadocJar()
    withSourcesJar()
}

// Configure Dokka to generate documentation
tasks.named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}


// Configure JReleaser
jreleaser {
    project {
        name = project.name
        description = project.description
        authors = listOf("BierDav")

        links {
            homepage = "https://bierdav.github.io/Kotlinmailer/"
        }
    }

    signing {
        active.set(org.jreleaser.model.Active.ALWAYS)
        armored.set(true)
        mode = Signing.Mode.MEMORY

        passphrase.set(findProperty("customSigningInMemoryKeyPassword")?.toString())
        secretKey.set(findProperty("customSigningInMemoryKey")?.toString())
    }

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("ALWAYS")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("target/staging-deploy")
                }
            }
        }
    }
}

tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
        documentedVisibilities.set(setOf(Visibility.PUBLIC, Visibility.PROTECTED))

        sourceLink {
            val exampleDir = "https://github.com/$githubProject/tree/master/"

            localDirectory.set(rootProject.projectDir)
            remoteUrl.set(URI(exampleDir).toURL())
            remoteLineSuffix.set("#L")
        }
    }
}
