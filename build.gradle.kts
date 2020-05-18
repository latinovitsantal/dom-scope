import org.gradle.jvm.tasks.Jar

val domScopeVersion = "0.0.4"

plugins {
    id("org.jetbrains.kotlin.js") version "1.3.72"
    `maven-publish`
}

group = "com.github.latinovitsantal"
version = domScopeVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-js"))
}

val jarSources = task<Jar>("jarSources") {
    archiveClassifier.set("sources")
    from(sourceSets)
}

publishing {
    repositories { mavenLocal() }
    publications {
        register("kotlinLib", MavenPublication::class) {
            from(components["kotlin"])
            artifact(jarSources)
        }
    }
}

