import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// ============================================================================
// Resource Navigator — Gradle Build Configuration
//
// Configures the Kotlin/JVM toolchain, IntelliJ Platform SDK, PyCharm target,
// plugin metadata, compatibility range, repositories, and test dependencies.
//
// PyCharm compatibility:
//   Minimum: PyCharm 2025.2 / IntelliJ Platform build 252
//   Target:  PyCharm 2026.2 / IntelliJ Platform build 262
//   Maximum: Unbounded
//
// Notes:
//   IntelliJ Platform fixture testing is enabled through TestFrameworkType.Platform.
//   Existing kotlin.test unit tests remain supported alongside Platform fixture tests.
//   If PyCharm 2026.2 again exposes ModuleDescriptor.Dependency parsing failures,
//   TestFrameworkType.Bundled is the documented fallback to testFramework.jar.
//
// RH:
//   v2.0.1 — 2026-07-26
//   - Migrated development target from PyCharm 2025.2 to PyCharm 2026.2.
//   - Migrated JVM toolchain from JDK 21 to JDK 25.
//   - Updated IntelliJ Platform Gradle Plugin from 2.13.1 to 2.18.1.
//   - Retained minimum compatibility with PyCharm 2025.2 (build 252).
//   - Removed upper IDE compatibility bound for forward compatibility.
//   - Removed unused IntelliJ Platform testFramework dependency.
//   - Verified buildPlugin and runtime resource navigation under PyCharm 2026.2.
//
//   v2.1.0 — 2026-08-25
//   - Reintroduced IntelliJ Platform fixture-test support for automated RN regressions.
//   - Added the Platform test framework while retaining kotlin.test unit tests.
//
//   v2.1.1 — 2026-10-02
//   - Excluded *_bak_* Kotlin backup files from all Kotlin compilation tasks.
// ============================================================================

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

// Resource Navigator targets Java 25 for PyCharm 2026.2.
kotlin {
    jvmToolchain(25)
}

// Keep local *_bak_* Kotlin backup files out of both production and test compilation.
kotlin.sourceSets.configureEach {
    kotlin.exclude("**/*_bak_*")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        pycharm("2026.2")
        bundledPlugin("PythonCore")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.jschelert.resource-navigator"
        name.set("Resource Navigator")
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }

        vendor {
            name.set("James Schelert")
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.PyCharm, "2025.2.6.1")
            create(IntelliJPlatformType.PyCharm, "2026.2")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}