plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("org.springframework.boot.aot")
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native") version "0.10.6"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// We only native-compile production code; test AOT processing chokes on
// springmockk's MockkDefinition and we don't need native tests anyway.
tasks.named("processTestAot") { enabled = false }

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

// Pin Logback to the version that has full GraalVM Reachability Metadata coverage.
// Spring Boot 3.5.3 ships 1.5.18 which is ahead of the RMR's 1.5.7 entry and
// triggers SLF4J `Reporter` build-time-init errors.
configurations.all {
    resolutionStrategy.force(
        "ch.qos.logback:logback-classic:1.5.7",
        "ch.qos.logback:logback-core:1.5.7",
    )
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("no.novari.linkwalker.reader.ReaderAppKt")
            buildArgs.add("--initialize-at-build-time=org.slf4j.helpers.Reporter,org.slf4j.LoggerFactory,org.slf4j.helpers.SubstituteServiceProvider,org.slf4j.helpers.NOP_FallbackServiceProvider")
            // Wire Spring Boot AOT outputs onto the native image classpath so the
            // generated `…__ApplicationContextInitializer` is reachable at runtime.
            classpath(
                tasks.named("compileAotJava"),
                tasks.named("compileAotKotlin"),
                tasks.named("processAotResources"),
                configurations.named("aotRuntimeClasspath"),
            )
        }
    }
}
