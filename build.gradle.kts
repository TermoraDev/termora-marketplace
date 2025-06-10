plugins {
    application
    kotlin("jvm") version "2.1.20"
}

group = "app.termora"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("app.termora.marketplace.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.dom4j:dom4j:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("commons-cli:commons-cli:1.9.0")
    implementation("org.kohsuke:github-api:1.327")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-io:commons-io:2.19.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}