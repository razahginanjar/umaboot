plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "io.umaboot"
version = "0.7.1-SNAPSHOT"

java {
    toolchain {
        // IntelliJ Platform 2024.2+ requires Java 21
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

repositories {
    mavenLocal()              // umaboot-core comes from `mvn install`
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Real wiring: depend on the published core JAR.
    // Run `mvn install -pl umaboot-core -am` in the parent project first to
    // populate ~/.m2/repository.
    implementation("io.umaboot:umaboot-core:0.1.0-SNAPSHOT") {
        // The IntelliJ Platform supplies its own SLF4J — exclude ours.
        exclude(group = "org.slf4j")
        exclude(group = "ch.qos.logback")
    }

    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.yaml")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242")
        }
    }
}

tasks {
    runIde {
        jvmArgs("-Didea.is.internal=true")
    }
}
