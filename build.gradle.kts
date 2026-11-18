plugins {
    `java-library`
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
}

group = "org.tectonicus"
version = "1.4.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Fail the build on missing or broken Javadoc in the public API.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        quiet()
    }
}

tasks.withType<Jar>().configureEach {
    metaInf { from("LICENSE") }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("JNBT")
                description.set("A Java library for reading and writing Minecraft NBT (Named Binary Tag) files.")
                url.set("https://github.com/tectonicus/JNBT")
                licenses {
                    license {
                        name.set("BSD-3-Clause")
                        url.set("https://opensource.org/license/bsd-3-clause")
                    }
                }
                developers {
                    developer {
                        id.set("grahamedgecombe")
                        name.set("Graham Edgecombe")
                        url.set("https://github.com/grahamedgecombe")
                    }
                    developer {
                        id.set("skoeven")
                        name.set("Steven Koeven")
                        url.set("https://github.com/skoeven")
                    }
                }
                scm {
                    url.set("https://github.com/tectonicus/JNBT")
                    connection.set("scm:git:https://github.com/tectonicus/JNBT.git")
                    developerConnection.set("scm:git:ssh://git@github.com/tectonicus/JNBT.git")
                }
            }
        }
    }
}

// Central requires signed artifacts. Provide an ASCII-armored key through the signingInMemoryKey and
// signingInMemoryKeyPassword Gradle properties (or ORG_GRADLE_PROJECT_* env vars); otherwise publishing to Central
// uses the gpg CLI. Local publishes are signed only when a key is configured.
// findProperty also sees ORG_GRADLE_PROJECT_* environment variables, unlike providers.gradleProperty.
// Blank values count as missing, since GitHub Actions passes an unset secret as an empty string.
fun nonBlankProperty(name: String): String? = (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val signingKey = nonBlankProperty("signingInMemoryKey")
signing {
    if (signingKey != null) {
        // Gradle creates no signatory for a null password; an unprotected key uses an empty one.
        useInMemoryPgpKeys(signingKey, findProperty("signingInMemoryKeyPassword") as String? ?: "")
    }
    // Without a signatory, signing is skipped unless required; publishing to Central requires it (below).
    isRequired = false
    sign(publishing.publications)
}

fun isCentralPublish(task: Task) = task.name.contains("CentralPortal")

// Decided from the task graph rather than the command line, so abbreviated task names are covered too.
gradle.taskGraph.whenReady {
    if (allTasks.any(::isCentralPublish)) {
        signing.isRequired = true
        if (signingKey == null) signing.useGpgCmd()
    }
}

// Publishing a release runs the checks first, and with -PreleaseTag verifies that the tag matches the version.
// Values are captured as locals so the configuration cache does not serialize script object references.
val verifyReleaseVersion = tasks.register("verifyReleaseVersion") {
    description = "Checks that the -PreleaseTag value, minus a leading 'v', matches the project version."
    val releaseTag = providers.gradleProperty("releaseTag")
    val projectVersion = project.version.toString()
    inputs.property("releaseTag", releaseTag.orElse(""))
    doLast {
        val tag = releaseTag.orNull ?: return@doLast
        check(tag.removePrefix("v") == projectVersion) {
            "Release tag '$tag' does not match project version '$projectVersion'. Update version in build.gradle.kts."
        }
    }
}

tasks.matching(::isCentralPublish).configureEach {
    dependsOn(verifyReleaseVersion, tasks.check)
}
