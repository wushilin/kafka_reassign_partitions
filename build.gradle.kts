import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.10"
    application
}

group = "net.wushilin"
version = "1.0-RELEASE"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    implementation("com.github.ajalt.clikt:clikt:3.5.2")
    implementation("org.apache.kafka:kafka-clients:3.4.0")
    implementation("net.wushilin:envawareproperties:1.0.6")
    implementation("org.slf4j:slf4j-simple:2.0.6")
}

tasks.test {
    useJUnitPlatform()
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    manifest {
        attributes(
            mapOf(
                "Main-Class" to "net.wushilin.kafka.tools.GenerateKafkaPartitionReassignmentKt",
                "Zip64" to "true"
            )
        )
    }

    from(configurations.runtimeClasspath.get().filter{ it -> it.exists() }.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}
