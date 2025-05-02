import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import java.net.URI

val githubProject = "BierDav/Kotinmailer"
var projectName = project.name
var projectGroup = project.group.toString()
var projectDescription = project.description
var projectVersion = project.version.toString()

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jreleaser")
}

// Configure Dokka to generate documentation
tasks.named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

publishing {
    publications {
        repositories {
            maven {
                setUrl(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }
}

jreleaser {
    project {
        name = projectName
        group = projectGroup
        description = projectDescription
        version = projectVersion
        authors = listOf("BierDav")

        links {
            homepage = "https://bierdav.github.io/Kotlinmailer/"
        }
    }

    signing {
        active = Active.ALWAYS
        armored = true
        mode = Signing.Mode.MEMORY

        passphrase= findProperty("customSigningInMemoryKeyPassword")?.toString()
        secretKey =findProperty("customSigningInMemoryKey")?.toString()
        publicKey=findProperty("customSigningInMemoryKey")?.toString()
    }

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    username = findProperty("mavenCentralUsername")?.toString()
                    password = findProperty("mavenCentralPassword")?.toString()

                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepositories = listOf("build/staging-deploy")
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
