plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "org.example"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opt"))
}

application {
    mainClass.set("examples.AnalysisKt")
}