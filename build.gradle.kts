plugins {
//    id("org.springframework.boot") version "2.5.5"
//    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux:2.5.5")

    // slack on bolt dependencies
    implementation("com.slack.api:bolt:1.12.1")
    implementation("com.slack.api:bolt-socket-mode:1.12.1")
    implementation("javax.websocket:javax.websocket-api:1.1")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.17")

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.5")
    testImplementation("io.projectreactor:reactor-test:3.4.11")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.register<JavaExec>("run") {
    classpath = sourceSets.getByName("main").runtimeClasspath
    main = "com.example.darby.DarbyApplication"
    args("--spring.profiles.active=prod")
}

