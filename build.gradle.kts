import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
    `maven-publish`
}

group = "com.sincegame.jsontomodel"
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        // 使用本机 IDE 作为编译目标平台，避免下载整个 IDE 发行版。
        // 如需改为下载远程平台（例如兼容更低版本），改用：
        //   defaultRepositories()
        // 并将下方 dependencies 中的 local(...) 换成 intellijIdeaCommunity("2023.3.8")
        localPlatformArtifacts()
        // 代码插桩（@NotNull 等）需要的 java-compiler-ant-tasks 等工具来自 JetBrains 仓库
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/intellij-repository/snapshots")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

dependencies {
    intellijPlatform {
        // 本机 IntelliJ IDEA 2024.1.7 (IU-241.19416.15)，JBR 17。
        // 编译产物 since-build=241，可运行于 IDEA 2024.1+ 与 DevEco Studio 6.x (平台 243+)。
        local("D:\\IntelliJ IDEA 2024.1.7")
    }
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.sincegame.jsontomodel"
        name = "JSON to ArkTS Model"
        version = project.version.toString()

        description = """
            Paste JSON, generate ArkTS model interfaces/classes.
            Supports interface / class output, optional parameterized constructor,
            nested object and array type inference. Works in IntelliJ IDEA and DevEco Studio.
        """.trimIndent()

        vendor {
            name = "sincegame"
        }

        ideaVersion {
            sinceBuild = "241"
            // 不设置 untilBuild，保证可以安装到所有更新版本的 IDE（包括 DevEco Studio 6.x / 未来版本）
            untilBuild = provider { "" }
        }
    }

    // Marketplace 插件签名（since-build 241 >= 2022.2，Marketplace 要求签名）
    // 证书/私钥获取方式：https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
    // 通过环境变量或 gradle.properties 提供：
    //   IJ_CERTIFICATE_CHAIN = 证书链 PEM 内容或文件路径
    //   IJ_PRIVATE_KEY       = RSA 私钥内容或文件路径
    //   IJ_PRIVATE_KEY_PASSWORD = 私钥密码（可选）
    signing {
        certificateChain = providers.environmentVariable("IJ_CERTIFICATE_CHAIN")
            .orElse(providers.gradleProperty("ij.certificateChain"))
        privateKey = providers.environmentVariable("IJ_PRIVATE_KEY")
            .orElse(providers.gradleProperty("ij.privateKey"))
        password = providers.environmentVariable("IJ_PRIVATE_KEY_PASSWORD")
            .orElse(providers.gradleProperty("ij.privateKeyPassword"))
    }

    // JetBrains Marketplace 发布
    // Token 获取：https://plugins.jetbrains.com/author/me/tokens （需 Permanent Token）
    publishing {
        token = providers.environmentVariable("IJ_PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("ij.publishToken"))
            .orNull
        // channels = listOf("default")   // 可用 "eap"/"beta" 等渠道灰度发布
        hidden = false
    }
}

tasks.test {
    useJUnit()
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// ----------------------------------------------------------------------------
// 发布到远程 Maven 仓库
//
// 用法：
//   gradlew publishPluginPublicationToRemoteRepository \
//       -PpublishRepoUrl=https://nexus.example.com/repository/maven-releases/ \
//       -PpublishRepoUsername=deployer -PpublishRepoPassword=*****
//
// 未传 publishRepoUrl 时，默认发布到 build/repo（本地目录仓库，可直接 file:// 引用或拷贝上传）。
// 产物为单 jar（插件主 jar，含 META-INF/plugin.xml），可直接作为 Gradle 依赖：
//   buildscript { dependencies { classpath "com.sincegame.jsontomodel:json-to-arkts:1.0.0" } }
// 或放入 IDE 的 plugins 目录 / 自定义插件仓库。
// ----------------------------------------------------------------------------
publishing {
    publications {
        create<MavenPublication>("plugin") {
            groupId = project.group.toString()
            artifactId = rootProject.name
            version = project.version.toString()

            // 优先发布 instrumented jar（与 buildPlugin 打进 zip 的主 jar 一致）
            val instrumented = tasks.findByName("instrumentedJar")
            if (instrumented != null) {
                artifact(instrumented) { classifier = "" }
            } else {
                artifact(tasks.jar.get()) { classifier = "" }
            }
            artifact(sourcesJar) { classifier = "sources" }

            pom {
                name.set("JSON to ArkTS Model")
                description.set("IntelliJ IDEA / DevEco Studio plugin: generate ArkTS model interfaces/classes from JSON")
            }
        }
    }
    repositories {
        maven {
            name = "remote"
            url = uri(providers.gradleProperty("publishRepoUrl")
                .orElse(layout.buildDirectory.dir("repo").map { it.asFile.toURI().toString() }))
            val repoUser = providers.gradleProperty("publishRepoUsername").orNull
            val repoPass = providers.gradleProperty("publishRepoPassword").orNull
            if (repoUser != null) {
                credentials {
                    username = repoUser
                    password = repoPass
                }
            }
        }
    }
}

tasks.register<Copy>("stageRepoArtifacts") {
    group = "publishing"
    description = "复制 buildPlugin 产物(zip)与 updatePlugins.xml 到 build/repo，便于搭建自定义插件仓库"
    dependsOn(tasks.buildPlugin)
    from(tasks.buildPlugin.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("repo/com/sincegame/jsontomodel/json-to-arkts/${project.version}"))
}
