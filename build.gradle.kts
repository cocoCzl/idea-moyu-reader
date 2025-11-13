import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

// === 插件基本信息 ===
group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

// === 仓库配置（国内建议加阿里云）===
repositories {
    // 阿里云镜像（加速 mavenCentral）
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    mavenCentral()

    // IntelliJ Platform 官方仓库（无法镜像，需网络通畅）
    intellijPlatform {
        defaultRepositories()
    }
}

// === 依赖 ===
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    implementation(libs.epublib)
    implementation(libs.jsoup)
    implementation(libs.pdfbox)

    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(providers.gradleProperty("platformBundledModules").map { it.split(',') })

        testFramework(TestFrameworkType.Platform)
    }
}

// === IntelliJ Platform 插件配置 ===
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion").get()

        // 从 README.md 提取描述
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

        // 从 CHANGELOG.md 提取更新日志
        val changelog = project.changelog
        changeNotes = providers.provider {
            changelog.renderItem(
                (changelog.getOrNull(project.version.toString()) ?: changelog.getUnreleased())
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        }

        ideaVersion {
            sinceBuild = "242" // IDEA 2024.2
            // untilBuild 默认为 242.*，兼容未来小版本
        }

        vendor {
            name = "cococzl"
            email = "your-email@example.com"
        }
    }

    // === 可选：如果你要发布到 Marketplace，取消注释以下块 ===
    /*
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map { v ->
            listOf(
                if (v.contains("-")) v.substringAfter("-").substringBefore(".") else "default"
            )
        }
    }
    */

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// === Changelog 配置 ===
changelog {
    groups.set(listOf("Added", "Changed", "Fixed"))
    repositoryUrl = "https://github.com/cococzl/idea-moyu-reader"
}

// === Kover 测试覆盖率 ===
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

// === 任务配置 ===
tasks {
    wrapper {
        gradleVersion = "8.3"
    }

    // 发布时自动打 changelog 标签
    publishPlugin {
        dependsOn(patchChangelog)
    }
}

// === UI 测试配置（可选）===
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}