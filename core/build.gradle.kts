plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

val fintVersion = "4.0.10"

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-actuator")

    api("no.novari:fint-utdanning-resource-model-java:${fintVersion}")
    api("no.novari:fint-administrasjon-resource-model-java:${fintVersion}")
    api("no.novari:fint-personvern-resource-model-java:${fintVersion}")
    api("no.novari:fint-okonomi-resource-model-java:${fintVersion}")
    api("no.novari:fint-ressurs-resource-model-java:${fintVersion}")
    api("no.novari:fint-arkiv-resource-model-java:${fintVersion}")

    api("no.novari:fint-core-metamodel:3.0.0")

    api("io.swagger.core.v3:swagger-annotations-jakarta:2.2.47")
    api("tools.jackson.module:jackson-module-kotlin:3.1.3")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    implementation(platform("com.azure:azure-sdk-bom:1.2.28"))
    implementation("com.azure:azure-storage-blob")
    implementation("com.azure:azure-identity")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("api.version", "1.45")
}
