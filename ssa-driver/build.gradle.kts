plugins {
    kotlin("multiplatform") version "2.0.0"
    application
}

group = "org.ssa"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
}

application {
    mainClass.set("OptStartupKt")
}

kotlin {
    jvm {
        withJava()
    }
    linuxX64 {
        binaries {
            executable {
                baseName = "OptStartup"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":ssa"))
            }
        }
    }
}

tasks.named<Jar>("jar") {
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        subprojects.flatMap { it.configurations.getByName("runtimeClasspath").files }
    })
}