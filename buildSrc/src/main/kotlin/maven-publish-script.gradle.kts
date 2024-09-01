import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import java.net.URI

val githubProject = "BierDav/Kotinmailer"

description = "A simple coroutine based Kotlin Email API for client projects"

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish")
}

val repositoryUsername = project.providers.gradleProperty("mavenCentralUsername")
val repositoryPassword = project.providers.gradleProperty("mavenCentralPassword")
println("username: $repositoryUsername, password: $repositoryPassword")

signing {
    val key = project.findProperty("customSigningInMemoryKey")?.toString()
    val password = project.findProperty("customSigningInMemoryKeyPassword")?.toString()
    println("Signing with key: $key and password: $password")
    useInMemoryPgpKeys(key, password)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = project.name,
        version = project.version.toString()
    )

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
        )
    )

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

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}

tasks.withType<DokkaTaskPartial>().configureEach {
    dokkaSourceSets.configureEach {
        displayName.set("kotlinmailer-$name")
        documentedVisibilities.set(setOf(Visibility.PUBLIC, Visibility.PROTECTED))

        // Read docs for more details: https://kotlinlang.org/docs/dokka-gradle.html#source-link-configuration
        sourceLink {
            val exampleDir = "https://github.com/$githubProject/tree/master/packages"

            localDirectory.set(rootProject.projectDir)
            remoteUrl.set(URI(exampleDir).toURL())
            remoteLineSuffix.set("#L")
        }
    }
}
