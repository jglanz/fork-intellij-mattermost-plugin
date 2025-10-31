@file:Suppress("ImplicitThis")

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.10.3"
    id("org.jetbrains.intellij.platform.migration") version "2.10.3"
}

group = "com.github.stefandotti"
//version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.2")
    }
}

val gsonVersion = "2.11.0"

intellijPlatform {

    pluginConfiguration {
        name = "Mattermost"
        description = "Mattermost integration for IntelliJ IDEA"
//
//        sinceBuild.set("252")
    }
}

tasks {

//    runIde {
//        autoReloadPlugins.set(true)
//    }
    withType<Copy> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Specifically, configure the jar task
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceSets {
        main {
            java.srcDirs("src/main/java")
            resources.srcDirs("src/main/resources")
        }
        test {
            java.srcDirs("src/test/java")
            resources.srcDirs("src/test/resources")
        }
    }
}

kotlin {
    // jvmToolchain(21)
    sourceSets {
        main {
            kotlin.srcDirs("src/main/kotlin")
//      resources.srcDirs("src/main/resources")
        }
        test {
            kotlin.srcDirs("src/test/kotlin")
//      resources.srcDirs("src/test/resources")
        }
    }
}

dependencies {
    // Replaced local JARs from libs/ with Maven Central coordinates
    implementation("com.google.code.gson:gson:$gsonVersion")

    // Apache HttpComponents (matching versions from libs/)
    implementation("org.apache.httpcomponents:httpclient:4.5.3")
    implementation("org.apache.httpcomponents:httpmime:4.5.3")
    implementation("org.apache.httpcomponents:httpclient-cache:4.5.3")
    implementation("org.apache.httpcomponents:httpclient-win:4.5.3")
    implementation("org.apache.httpcomponents:httpcore:4.4.6")
    implementation("org.apache.httpcomponents:fluent-hc:4.5.3")

    // Jackson (matching versions from libs/)
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.7.4")
    implementation("com.fasterxml.jackson.core:jackson-core:2.7.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.7.4")

    // Commons libraries
    implementation("commons-io:commons-io:2.5")
    implementation("commons-codec:commons-codec:1.9")
    implementation("commons-logging:commons-logging:1.2")

    // WebSocket
    implementation("org.java-websocket:Java-WebSocket:1.3.6")

    // Emoji
    implementation("com.kcthota:emoji4j:5.0")
    // Note: Original libs/ had emoji4j-5.0.jar; the maintained Maven artifact is emoji-java.
    // If you strictly need emoji4j 5.0, replace the above with: implementation("com.github.aayushatharva:emoji4j:5.0")

    // JNA
    implementation("net.java.dev.jna:jna:4.1.0")
    implementation("net.java.dev.jna:jna-platform:4.1.0")

    // Lambdaj
    implementation("com.googlecode.lambdaj:lambdaj:2.3.3")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("org.hamcrest:hamcrest-all:1.3")
}

tasks.test {
    useJUnitPlatform()
}
