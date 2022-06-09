val githubProject = "BierDav/Kotinmailer"

description = "A simple coroutine based Kotlin Email API for client projects"

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("signing")
}

signing {
    sign(publishing.publications)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/") {
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
