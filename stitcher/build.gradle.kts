plugins {
    java
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "dev.kikugie"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0-RC")
    testImplementation("com.charleskorn.kaml:kaml:0.57.0")
    testImplementation("net.orandja.obor:obor:0.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("com.github.ajalt.mordant:mordant:2.2.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(16)
}

publishing {
    repositories {
        maven {
            name = "kikugieMaven"
            url = uri("https://maven.kikugie.dev/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create("basic", BasicAuthentication::class)
            }
        }
    }

    publications {
        register("mavenJava", MavenPublication::class) {
            groupId = project.group.toString()
            artifactId = "stitcher"
            version = project.version.toString()
            artifact(tasks.getByName("jar"))
        }
    }
}