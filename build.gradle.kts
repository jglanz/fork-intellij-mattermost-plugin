plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.github.stefandotti"
version = "1.2.0"

repositories {
    mavenCentral()
}

val gsonVersion = "2.11.0"

intellij {
    version.set("2025.2")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}


tasks {
    patchPluginXml {
        sinceBuild.set("252")
//        untilBuild.set(null)
    }

    runIde {
        autoReloadPlugins.set(true)
    }
  withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }
  
  // Specifically, configure the jar task
  withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }
}

kotlin {
    // jvmToolchain(21)
  sourceSets {
    main {
      kotlin.srcDirs("src/main/kotlin")
      resources.srcDirs("src/main/resources")
    }
    test {
      kotlin.srcDirs("src/test/kotlin")
      resources.srcDirs("src/test/resources")
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
    implementation("com.vdurmont:emoji-java:5.1.1")
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
