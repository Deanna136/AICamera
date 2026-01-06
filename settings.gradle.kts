pluginManagement {
    repositories {
        // 阿里云镜像（加速 Gradle 插件下载）
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        // 腾讯云镜像（加速 Gradle 插件下载）
        maven {
            url = uri("https://mirrors.cloud.tencent.com/maven/")
        }
        // 保留 google 插件仓库，用正则过滤
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像（加速依赖下载）
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        // 腾讯云镜像（加速依赖下载）
        maven {
            url = uri("https://mirrors.cloud.tencent.com/maven/")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "AiCamera"
include(":app")