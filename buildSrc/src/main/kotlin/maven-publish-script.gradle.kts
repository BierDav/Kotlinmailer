import org.jreleaser.model.Active
import org.jreleaser.model.Signing

val githubProject = "BierDav/Kotlinmailer"
val projectName: String = project.name

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jreleaser")
}

// Maven Central requires a javadoc jar for every publication
val dokkaJar = tasks.register<Jar>("dokkaJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(dokkaJar)

        pom {
            name = projectName
            description = provider { project.description }
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

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    signing {
        active = Active.ALWAYS
        pgp {
            active = Active.ALWAYS
            armored = true
            mode = Signing.Mode.MEMORY

            passphrase = providers.gradleProperty("signingPassphrase")
            secretKey = providers.gradleProperty("signingSecretKey")
            publicKey = providers.gradleProperty("signingPublicKey")
        }
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

dokka {
    moduleName.set(projectName)
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(rootProject.projectDir)
            remoteUrl("https://github.com/$githubProject/tree/main")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        customAssets.from(rootProject.file(".idea/icon.png"))
        footerMessage.set("(c) David Emanuel Bieregger")
    }
}

// Kotlin/Native, JS and Wasm publications contain a klib instead of a jar
afterEvaluate {
    jreleaser.deploy.maven.mavenCentral.getByName("sonatype") {
        publishing.publications.withType<MavenPublication>()
            .filter { it.name != "jvm" && it.name != "kotlinMultiplatform" }
            .forEach { publication ->
                artifactOverride {
                    artifactId = publication.artifactId
                    jar = false
                    // the POM checker doesn't know the klib packaging
                    verifyPom = false
                }
            }
    }
}
