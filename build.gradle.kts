plugins {
    kotlin("jvm") version "2.1.20"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.lavalink.dev/releases")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:6.1.1")
    implementation("dev.arbjerg:lavaplayer:2.2.6")
    implementation("dev.lavalink.youtube:common:1.16.0")
}

kotlin {
    jvmToolchain(19)
}

tasks.jar {
    // FAILURE: Build failed with an exception.
    // > Entry META-INF/versions/9/module-info.class is a duplicate but no duplicate handling strategy has been set.
    // Please refer to https://docs.grdle.org/7.2/dsl/org.gradle.api.tasks.Copy.html#org.gradle.api.tasks.Copy:duplicatesStrategy for details.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "org.example.MusicBot"
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}