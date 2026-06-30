import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    // 2.2.x is required to read the Kotlin 2.2 metadata in the 2025.3 platform jars.
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.hotpath"
version = "0.1.14"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against a PhpStorm distribution so PHP PSI is available. We compile against 2025.3
        // (our minimum supported build) so we can use APIs introduced there — notably the modern,
        // non-deprecated daemon-restart call — instead of the variants deprecated since 2025.3.
        phpstorm("2025.3")
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
            // Minimum 2025.3: lets us call the non-deprecated daemon-restart API (the older
            // overloads were deprecated in 2025.3). Drops 2024.2–2025.2.
            sinceBuild = "253"
            // No upper bound: a provider yielding null *omits* the until-build attribute
            // entirely, so the plugin installs on 2025.3 and every future build. (An empty
            // string would instead write an invalid `until-build=""`, and leaving this unset
            // would let Gradle auto-cap it at "253.*".)
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The build we compile against (no extra download); also our minimum supported build.
            // (The PHP/JS-optional install path was verified against IntelliJ IDEA Community in
            // 0.1.12 and is unchanged since.)
            ide(IntelliJPlatformType.PhpStorm, "2025.3")
        }
    }

    // `./gw publishPlugin` uploads build/distributions/<version>.zip to the JetBrains Marketplace.
    // The token is read from the PUBLISH_TOKEN env var (a Marketplace Hub permanent token) so it
    // never lives in the repo. Get one at https://plugins.jetbrains.com/author/me/tokens.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
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
