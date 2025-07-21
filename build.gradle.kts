plugins {
    kotlin("jvm") version "2.1.21"
    `java-gradle-plugin`
    `maven-publish`
    id("java-library")
}

group = "org.jikvict"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // JUnit Platform
    implementation("org.junit.platform:junit-platform-launcher:1.10.1")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    
    // Gradle API
    implementation(gradleApi())
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
    // Allow tests to fail without failing the build
    // This is useful for our sample tests that are designed to fail
    ignoreFailures = true
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("jikvictTestingPlugin") {
            id = "org.jikvict.testing"
            implementationClass = "org.jikvict.testing.gradle.JIkvictTestingPlugin"
        }
    }
}

// Create a sources JAR
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}
publishing {
    publications {
        // Library publication
        create<MavenPublication>("jikvictTesting") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "JIkvictTestingLibrary"
            version = project.version.toString()
            
            // Include sources JAR
            artifact(sourcesJar)
        }
        
        // Plugin publication is automatically created by the java-gradle-plugin
        // We need to configure it to use a different artifactId to avoid conflicts
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                artifactId = "JIkvictTestingPlugin"
                
                // Include sources JAR for plugin as well
                artifact(sourcesJar)
            }
        }
    }
    repositories {
        mavenLocal()
    }
}