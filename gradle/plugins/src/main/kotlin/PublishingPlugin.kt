import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.GradlePublishPlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.jetbrains.dokka.gradle.DokkaTask

class PublishingPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.vanniktech.maven.publish")
        pluginManager.apply("org.jetbrains.dokka")

        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            tasks.named("processResources", ProcessResources::class.java) { processResources ->
                processResources.from(rootProject.file("LICENSE"))
            }
        }

        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            coordinates(group.toString(), name, version.toString())

            signAllPublications()

            configureBasedOnAppliedPlugins()

            pom { pom ->
                pom.name.set("${project.group}:${project.name}")
                pom.description.set(project.description)
                pom.url.set("https://github.com/usefulness/ktlint-gradle-plugin")
                pom.licenses { licenses ->
                    licenses.license { license ->
                        license.name.set("Apache-2.0")
                        license.url.set("https://github.com/usefulness/ktlint-gradle-plugin/blob/master/LICENSE")
                    }
                }
                pom.developers { developers ->
                    developers.developer { developer ->
                        developer.id.set("mateuszkwiecinski")
                        developer.name.set("Mateusz Kwiecinski")
                        developer.email.set("36954793+mateuszkwiecinski@users.noreply.github.com")
                    }
                    developers.developer { developer ->
                        developer.id.set("jeremymailen")
                        developer.name.set("Jeremy Mailen")
                    }
                }
                pom.scm { scm ->
                    scm.connection.set("scm:git:github.com/usefulness/ktlint-gradle-plugin.git")
                    scm.developerConnection.set("scm:git:ssh://github.com/usefulness/ktlint-gradle-plugin.git")
                    scm.url.set("https://github.com/usefulness/ktlint-gradle-plugin/tree/master")
                }
            }
        }

        extensions.configure<PublishingExtension> {
            with(repositories) {
                maven { maven ->
                    maven.name = "github"
                    maven.setUrl("https://maven.pkg.github.com/usefulness/ktlint-gradle-plugin")
                    with(maven.credentials) {
                        username = "usefulness"
                        password = findConfig("GITHUB_TOKEN")
                    }
                }
            }
        }
        pluginManager.withPlugin("com.gradle.plugin-publish") {
            extensions.configure<GradlePluginDevelopmentExtension>("gradlePlugin") { gradlePlugin ->
                gradlePlugin.apply {
                    website.set("https://github.com/usefulness/ktlint-gradle-plugin")
                    vcsUrl.set("https://github.com/usefulness/ktlint-gradle-plugin")
                }
            }
        }
    }

    private inline fun <reified T: Any> ExtensionContainer.configure(crossinline receiver: T.() -> Unit) {
        configure(T::class.java) { receiver(it) }
    }
}

private fun Project.findConfig(key: String): String {
    return findProperty(key)?.toString() ?: System.getenv(key) ?: ""
}
