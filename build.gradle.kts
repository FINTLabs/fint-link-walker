plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "no.novari"
    version = project.findProperty("version") ?: "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven(url = "https://repo.fintlabs.no/releases")
    }
}
