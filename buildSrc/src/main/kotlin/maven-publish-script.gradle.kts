import java.net.URI

val githubProject = "BierDav/Kotinmailer"

description = "A simple coroutine based Kotlin Email API for client projects"

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("signing")
}

signing {
    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val privateKeyPassword = System.getenv("GPG_PRIVATE_KEY_PASSWORD")

    if (privateKey == null || privateKeyPassword == null) {
        println("Private key and/or associated Password for signing is missing. For local development this can be ignored, because it is only required for publishing.")
        return@signing
    }
    useInMemoryPgpKeys(privateKey, privateKeyPassword)

    sign(publishing.publications)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            val snapshotRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            val releaseRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val repoUrl = if (project.version.toString().endsWith("SNAPSHOT")) snapshotRepoUrl else releaseRepoUrl

            url = URI.create(repoUrl)
            name = "ossrh"
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])

            this.groupId = project.group.toString()
            this.artifactId = project.name
            this.version = project.version.toString()

            pom {
                name.set(project.name)
                description.set(project.description)

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

                url.set("https://github.com/$githubProject")

                scm {
                    connection.set("scm:git:git://github.com/$githubProject.git")
                    url.set("https://github.com/$githubProject/tree/main")
                }
            }
        }
    }
}
