plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktor_version: String by project

dependencies {
    implementation("io.ktor:ktor-client-core:${ktor_version}")
    implementation("io.ktor:ktor-client-cio:${ktor_version}")
    implementation("io.ktor:ktor-server-netty:${ktor_version}")
    implementation("io.ktor:ktor-client-okhttp:${ktor_version}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktor_version}")
    implementation("io.ktor:ktor-server-core:${ktor_version}")
    implementation("io.ktor:ktor-server-netty:${ktor_version}")
    implementation("io.ktor:ktor-client-websockets:2.3.9")
    implementation("org.json:json:20240303")


    implementation("io.ktor:ktor-server-content-negotiation:${ktor_version}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    testImplementation(kotlin("test"))
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.github.ajalt.clikt:clikt:4.2.1")

    // Kotlin расширения Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
}

application {
    mainClass.set("com.example.igmcpclient.IgMcpClientKt")
}

tasks.shadowJar {
    archiveBaseName.set("ig-mcp-client")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "com.example.igmcpclient.IgMcpClientKt"
        )
    }
}


kotlin {
    jvmToolchain(21)
}