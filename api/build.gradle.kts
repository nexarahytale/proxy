plugins {
    `java-library`
    `maven-publish`
}

group = "me.internalizable.numdrassl"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Annotations
    compileOnlyApi("com.google.code.findbugs:jsr305:3.0.2")

    // Logging (API only)
    api("org.slf4j:slf4j-api:2.0.9")

    // Gson for JSON handling
    api("com.google.code.gson:gson:2.10.1")

    // Guava for utilities
    api("com.google.guava:guava:32.1.3-jre")

    // Event system annotations
    compileOnlyApi("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}

tasks {
    jar {
        manifest {
            attributes["Automatic-Module-Name"] = "me.internalizable.numdrassl.api"
        }
    }

    withType<Javadoc> {
        val o = options as StandardJavadocDocletOptions
        o.encoding = "UTF-8"
        o.source = "17"
        o.use()
        o.addStringOption("Xdoclint:none", "-quiet")
    }

    withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:-unchecked", "-Xlint:-deprecation"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Numdrassl API")
                description.set("API for extending Numdrassl Hytale QUIC Proxy")
            }
        }
    }
}

