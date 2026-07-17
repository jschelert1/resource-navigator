plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        pycharm("2025.2.6.1")
        bundledPlugin("PythonCore")
        testFramework(
            org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform
        )
    }

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = "com.jschelert.resource-navigator"
        name = "Resource Navigator"
        version = project.version.toString()

        description =
            "Navigate local files and URLs referenced from supported Python string literals."

        ideaVersion {
            sinceBuild = "252"
            untilBuild = "252.*"
        }

        vendor {
            name = "James Schelert"
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}