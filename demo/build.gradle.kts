import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.link

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.varabyte.kobweb.application")
}

group = "at.quickme.kotlinmailer.demo"

repositories {
    mavenCentral()
    google()
}

kobweb {
    app {
        index {
            description.set("Send emails over JMAP from the browser with Kotlinmailer")
            faviconPath.set("/icon.png")
            head.add {
                link(rel = "stylesheet", href = "styles.css")
            }
        }
    }
}

kotlin {
    configAsKobwebApplication("kotlinmailer-demo")

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(project(":packages:jmap"))
            implementation(project(":packages:html"))
            implementation(libs.ktor.client.js)
        }
    }
}
