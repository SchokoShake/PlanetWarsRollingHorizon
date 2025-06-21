plugins {
    kotlin("jvm") version "1.9.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("plugin.serialization") version "1.9.10"
}

group = "sml"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // The flatDir for local libs is kept
    flatDir {
        dirs("libs")
    }
}

dependencies {
    // Ktor Server and Client Dependencies (Standardized on 2.3.10)
    implementation("io.ktor:ktor-server-core-jvm:2.3.10")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.10")
    implementation("io.ktor:ktor-server-websockets:2.3.10")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.10")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.10")
    implementation("io.ktor:ktor-client-core:2.3.10")
    implementation("io.ktor:ktor-client-cio:2.3.10")

    // Kotlinx Serialization (already declared by ktor plugin, but explicit is ok)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Local Jar dependency
    implementation(files("libs/ntbea.jar"))

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    // ========================================================================
    // INCOMPATIBLE DEPENDENCIES - These have been removed to fix the server.
    // implementation("com.google.guava:guava:32.1.2-jre") // Spark brings an old version
    // implementation("com.tencent.angel:angel-automl:0.1.0")
    // implementation("org.apache.spark:spark-core_2.11:2.4.0")
    // implementation("org.apache.spark:spark-mllib_2.11:2.4.0")
    // implementation("org.apache.spark:spark-sql_2.11:2.4.0")
    // implementation("org.scala-lang:scala-library:2.11.12")
    // ========================================================================
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(20)
    }
}

application {
    // You may need to change this back to your Optuna runner main class for testing
    mainClass.set("games.planetwars.view.RunVisualGameKt")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("client-server")
    archiveClassifier.set("")
    archiveVersion.set("")
}