import org.gradle.api.attributes.Bundling
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    // IntelliJ Platform 2025.2 runs on JVM 21. Pin the toolchain so the build is
    // reproducible regardless of the JDK that happens to run Gradle.
    jvmToolchain(21)
}

/**
 * ktlint はサードパーティの Gradle プラグインを挟まず、CLI を直接呼ぶ。
 * 構成キャッシュとの相性が良く、更新も版番号 1 か所で済む。
 */
val ktlint: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("version")

        // Take the plugin description from README.md between the marker comments.
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        // Take the change notes from the latest CHANGELOG.md release entry.
        changeNotes = providers.gradleProperty("version").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

/** 検査対象。ビルド生成物とテストデータは含めない。 */
val ktlintSources = listOf("src/**/*.kt", "*.kts")

val ktlintCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Kotlin ソースのコードスタイルを検査する"
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    args(ktlintSources)
    // ktlint が内部で使う Kotlin コンパイラの都合で必要
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val ktlintFormat by tasks.registering(JavaExec::class) {
    group = "formatting"
    description = "Kotlin ソースのコードスタイル違反を自動修正する"
    classpath = ktlint
    mainClass = "com.pinterest.ktlint.Main"
    args(listOf("--format") + ktlintSources)
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

tasks.check {
    dependsOn(ktlintCheck)
}
