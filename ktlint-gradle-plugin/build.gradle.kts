import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-gradle-plugin")
    id("com.starter.publishing")
    id("com.starter.library.kotlin")
}

description = "Lint and formatting for Kotlin using ktlint with configuration-free setup on JVM and Android projects"

configurations {
    register("testRuntimeDependencies") {
        extendsFrom(compileOnly.get())
        attributes {
            // KGP publishes multiple variants https://kotlinlang.org/docs/whatsnew17.html#support-for-gradle-plugin-variants
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
        }
    }
    configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin")) {
                useVersion(getKotlinPluginVersion())
            }
        }
    }
}

dependencies {
    compileOnly(libs.kotlin.gradle)
    compileOnly(libs.agp.gradle)
    compileOnly(libs.ktlint.core)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.commons.io)
}

kotlin {
    jvmToolchain(17)
}

tasks {
    val generateVersionProperties = register("generateVersionProperties") {
        val projectVersion = version
        val ktlintVersion = libs.versions.maven.ktlint.get()
        val propertiesFile = File(sourceSets.main.get().output.resourcesDir, "version.properties")
        inputs.property("projectVersion", projectVersion)
        outputs.file(propertiesFile)

        doLast {
            propertiesFile.writeText(
                """
                version = $projectVersion
                ktlint_version = $ktlintVersion
                
                """.trimIndent(),
            )
        }
    }

    processResources {
        dependsOn(generateVersionProperties)
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.4"
            languageVersion = "1.4"
        }
    }
    withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Required to put the Kotlin plugin on the classpath for the functional test suite
    withType<PluginUnderTestMetadata>().configureEach {
        pluginClasspath.from(configurations.getByName("testRuntimeDependencies"))
    }
}

gradlePlugin {
    plugins {
        create("ktlintPlugin") {
            id = "io.github.usefulness.ktlint-gradle-plugin"
            displayName = "ktlint Gradle plugin"
            description = project.description
            tags.addAll(listOf("kotlin", "ktlint", "lint", "format", "style", "android"))
            implementationClass = "io.github.usefulness.KtlinGradlePlugin"
        }
    }
}
