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
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}