plugins {
    kotlin("multiplatform") version "2.0.0"
    distribution
}

group = "org.ssa"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://repo1.maven.org/maven2/") }
    maven {
        url = uri("https://repo.spring.io/release")
    }
    maven {
        url = uri("https://repository.jboss.org/maven2")
    }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
}

kotlin {
    jvm {

    }
    linuxX64 {
        binaries {
            executable()
        }
    }

    dependencies {
        commonMainImplementation("org.jetbrains.kotlin:kotlin-stdlib-common")
        commonTestImplementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        commonTestImplementation("org.jetbrains.kotlin:kotlin-test-common")
        commonMainImplementation("com.squareup.okio:okio:3.9.0")

        add("jvmTestImplementation", "org.jetbrains.kotlin:kotlin-test-junit")
        add("jvmTestImplementation", "junit:junit:4.13")
        add("jvmMainImplementation", "org.jetbrains.kotlin:kotlin-compiler")
    }
}