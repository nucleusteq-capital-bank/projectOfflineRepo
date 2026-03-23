import java.net.URL
import java.io.File

plugins {
    id("java")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val offlineRepoDir = layout.buildDirectory.dir("offline-repo")

fun downloadArtifact(group: String, name: String, version: String) {

    val groupPath = group.replace(".", "/")
    val baseUrl = "https://repo.maven.apache.org/maven2/$groupPath/$name/$version"

    val targetDir = File(offlineRepoDir.get().asFile, "$groupPath/$name/$version")
    targetDir.mkdirs()

    val jarUrl = "$baseUrl/$name-$version.jar"
    val pomUrl = "$baseUrl/$name-$version.pom"

    val jarFile = File(targetDir, "$name-$version.jar")
    val pomFile = File(targetDir, "$name-$version.pom")

    try {
        println("⬇️ Downloading $name-$version.jar")

        URL(jarUrl).openStream().use { input ->
            jarFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        URL(pomUrl).openStream().use { input ->
            pomFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

    } catch (e: Exception) {
        println("❌ Failed to download $group:$name:$version")
    }
}

// ----------------------------------
// MAIN OFFLINE DEPENDENCIES
// ----------------------------------
val offlineDependencies = configurations.create("offlineDependencies") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = true
}

dependencies {

    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-web:3.5.6")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-data-jpa:3.5.6")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-security:3.5.6")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-validation:3.5.6")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-data-redis:3.5.6")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.5.6")

    add("offlineDependencies", "com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre11")

    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-test:3.5.6")

    add("offlineDependencies", "org.springframework.boot:spring-boot-gradle-plugin:3.5.6")
    add("offlineDependencies", "io.spring.gradle:dependency-management-plugin:1.1.6")
    add("offlineDependencies", "org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:4.4.1.3373")
}

// ----------------------------------
// BUILD OFFLINE REPO
// ----------------------------------
tasks.register("buildOfflineRepo") {

    doLast {

        println("Resolving dependencies...")
        offlineDependencies.resolve()

        val repoRoot = offlineRepoDir.get().asFile
        val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")

        println("Copying from Gradle cache...")

        gradleCache.walkTopDown().forEach { file ->

            if (file.isFile && file.extension == "jar") {

                val parts = file.absolutePath.split("/files-2.1/")[1].split("/")

                if (parts.size < 4) return@forEach

                val group = parts[0]
                val name = parts[1]
                val version = parts[2]

                val groupPath = group.replace(".", "/")

                val targetDir = File(repoRoot, "$groupPath/$name/$version")
                targetDir.mkdirs()

                file.copyTo(
                    File(targetDir, "$name-$version.jar"),
                    overwrite = true
                )

                val pomFile = File(targetDir, "$name-$version.pom")

                if (!pomFile.exists()) {
                    val pomUrl = "https://repo.maven.apache.org/maven2/" +
                            "$groupPath/$name/$version/$name-$version.pom"

                    try {
                        URL(pomUrl).openStream().use { input ->
                            pomFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (_: Exception) {
                        println("⚠️ Missing POM for $name:$version")
                    }
                }
            }
        }

        // 🔥 MANUAL FIX — THIS IS THE KEY
        println("🚀 Injecting Kotlin manually...")
        downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.25")

        println("✅ OFFLINE REPO READY (INCLUDING KOTLIN)")
    }
}