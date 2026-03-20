import java.io.File

plugins {
    base
}

// --------------------------------------------
// CONFIG
// --------------------------------------------
val repoDir = file("offline-repo")

// --------------------------------------------
// TASK: Build Offline Repo from Gradle Cache
// --------------------------------------------
tasks.register("buildOfflineRepo") {

    group = "offline"
    description = "Build offline repo from existing Gradle cache"

    doLast {

        println("========================================")
        println("STEP 1: Using existing Gradle cache")
        println("========================================")

        val cacheRoot = File(System.getProperty("user.home"))
            .resolve(".gradle/caches/modules-2/files-2.1")

        if (!cacheRoot.exists()) {
            throw GradleException(" Gradle cache not found at: $cacheRoot")
        }

        println("Cache location: $cacheRoot")

        // Clean previous repo
        repoDir.deleteRecursively()
        repoDir.mkdirs()

        println("========================================")
        println("STEP 2: Copying artifacts")
        println("========================================")

        var count = 0

        cacheRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "pom") }
            .forEach { file ->

                val relativePath = file.absolutePath
                    .substringAfter("files-2.1${File.separator}")

                val parts = relativePath.split(File.separator)

                if (parts.size >= 4) {

                    val group = parts[0]
                    val module = parts[1]
                    val version = parts[2]

                    val targetDir = repoDir
                        .resolve(group.replace(".", "/"))
                        .resolve(module)
                        .resolve(version)

                    targetDir.mkdirs()

                    val targetFile = targetDir.resolve(file.name)

                    try {
                        file.copyTo(targetFile, overwrite = true)
                        count++
                    } catch (e: Exception) {
                        println("Skipped: ${file.name}")
                    }
                }
            }

        println("========================================")
        println(" Artifacts copied: $count")
        println(" Repo created at: ${repoDir.absolutePath}")
        println("========================================")
    }
}

// --------------------------------------------
// TASK: Build Docker Image
// --------------------------------------------
tasks.register("buildOfflineRepoImage") {

    group = "offline"
    description = "Build Docker image for offline repo"

    dependsOn("buildOfflineRepo")

    doLast {

        println("========================================")
        println("STEP 3: Building Docker Image")
        println("========================================")

        val process = ProcessBuilder(
            "docker", "build",
            "-t", "offline-repo:latest",
            "."
        )
            .inheritIO()
            .start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw GradleException(" Docker build failed")
        }

        println("========================================")
        println(" Docker image created: offline-repo:latest")
        println("========================================")
    }
}