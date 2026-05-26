// The Foojay resolver auto-downloads any required JDK (e.g. 21) via the
// Disco-API when the requested toolchain isn't installed locally. With this,
// a user with only JDK 17 on their machine can still build the plugin —
// Gradle fetches a 21 toolchain transparently and caches it under
// ~/.gradle/jdks/.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "umaboot-intellij"
