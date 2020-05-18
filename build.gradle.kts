plugins {
    id("org.jetbrains.kotlin.js") version "1.3.72"
    maven
}

group = "com.github.latinovitsantal"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-js"))
}

