import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.hotpath"
version = "0.1.6"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against a PhpStorm distribution so PHP PSI is available.
        phpstorm("2024.2.4")
        // PHP language support is shipped as a bundled plugin inside PhpStorm.
        bundledPlugin("com.jetbrains.php")
        // JavaScript/TypeScript support, also bundled in PhpStorm.
        bundledPlugin("JavaScript")

        pluginVerifier()
        zipSigner()

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    // The platform test framework's assertions are built on opentest4j.
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            // No upper bound: a provider yielding null *omits* the until-build attribute
            // entirely, so the plugin installs on 2024.2 and every future build. (An empty
            // string would instead write an invalid `until-build=""`, and leaving this unset
            // would let Gradle auto-cap it at "242.*".)
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The build we compile against (no extra download).
            ide(IntelliJPlatformType.PhpStorm, "2024.2.4")
            // The newer line we now claim compatibility with (downloads ~1 GB on first run).
            ide(IntelliJPlatformType.PhpStorm, "2025.3")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    wrapper {
        gradleVersion = "8.13"
    }

    // Optional optimization that pre-indexes the settings search box. It spins up a headless IDE
    // and is flaky with the bundled JavaScript plugin loaded; the plugin works fine without it.
    buildSearchableOptions {
        enabled = false
    }
}
