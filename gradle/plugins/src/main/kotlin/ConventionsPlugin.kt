import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

class ConventionsPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            val kotlinVersion = getKotlinPluginVersion()
            configurations.matching { it.name != "detekt" && !it.name.startsWith("ktlint") }.configureEach { configuration ->
                configuration.resolutionStrategy.eachDependency { details ->
                    if (details.requested.group == "org.jetbrains.kotlin" && details.requested.name.startsWith("kotlin")) {
                        details.useVersion(kotlinVersion)
                    }
                }
            }
            extensions.configure(KotlinJvmProjectExtension::class.java) { kotlin ->
                kotlin.jvmToolchain(libs.findVersion("java-compilation").get().requiredVersion.toInt())
            }
        }

        pluginManager.withPlugin("java") {
            if (hasProperty("skipJarVersion")) {
                val projectName = target.name
                tasks.named("jar", Jar::class.java) { jar ->
                    jar.archiveFileName.set("$projectName.jar")
                }
            }
        }
    }
}
