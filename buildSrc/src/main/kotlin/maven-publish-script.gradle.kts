import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import java.net.URI

val githubProject = "BierDav/Kotlinmailer"
var projectName = project.name
var projectDescription = project.description

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

val dokkaJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaHtml"))
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(dokkaJar.get())

        pom {
            name = projectName
            description = projectDescription
            url = "https://github.com/$githubProject"

            developers {
                developer {
                    name.set("BierDav")
                    organization.set("QuickMe")
                    organizationUrl.set("https://quickme.at")
                }
            }

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/$githubProject.git")
                url.set("https://github.com/$githubProject/tree/main")
            }
        }
    }

    publications {
        repositories {
            maven {
                setUrl(layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }
}

jreleaser {
    signing {
        active = Active.ALWAYS
        armored = true
        mode = Signing.Mode.MEMORY

        passphrase = providers.gradleProperty("signingPassphrase")
        secretKey = providers.gradleProperty("signingSecretKey")
        publicKey = providers.gradleProperty("signingPublicKey")
    }

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    username = providers.gradleProperty("mavenCentralUsername")
                    password = providers.gradleProperty("mavenCentralPassword")

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
