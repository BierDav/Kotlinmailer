import org.jreleaser.model.Active
import org.jreleaser.model.Signing

val githubProject = "BierDav/Kotlinmailer"
val projectName: String = project.name
val projectDescription: String? = project.description

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("org.jetbrains.dokka")
    id("org.jreleaser")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
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
