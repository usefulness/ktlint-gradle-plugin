import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugins.signing.SigningExtension

class PublishingPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("maven-publish")
        pluginManager.apply("signing")
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
                maven { maven ->
                    maven.name = "mavenCentral"
                    maven.setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    maven.mavenContent { it.releasesOnly() }
                    with(maven.credentials) {
                        username = findConfig("OSSRH_USERNAME")
                        password = findConfig("OSSRH_PASSWORD")
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

        pluginManager.withPlugin("signing") {
            with(extensions.extraProperties) {
                set("signing.keyId", findConfig("SIGNING_KEY_ID"))
                set("signing.password", findConfig("SIGNING_PASSWORD"))
                set("signing.secretKeyRingFile", findConfig("SIGNING_SECRET_KEY_RING_FILE"))
            }

            extensions.configure<SigningExtension>("signing") { signing ->
                if (findConfig("SIGNING_PASSWORD").isNotEmpty()) {
                    signing.sign(extensions.getByType(PublishingExtension::class.java).publications)
                }
            }
        }

        pluginManager.withPlugin("java") {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
            }
            extensions.configure<PublishingExtension> {
                publications.configureEach { publication ->
                    (publication as? MavenPublication)?.pom { pom ->
                        pom.name.set("${project.group}:${project.name}")
                        pom.description.set(project.description)
                        pom.url.set("https://github.com/usefulness/ktlint-gradle-plugin")
                        pom.licenses { licenses ->
                            licenses.license { license ->
                                license.name.set("The Apache Software License, Version 2.0")
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
            }
        }
    }

    private inline fun <reified T> ExtensionContainer.configure(crossinline receiver: T.() -> Unit) {
        configure(T::class.java) { receiver(it) }
    }
}

private fun Project.findConfig(key: String): String {
    return findProperty(key)?.toString() ?: System.getenv(key) ?: ""
}
