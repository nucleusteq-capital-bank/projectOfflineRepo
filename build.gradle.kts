import java.io.File
import java.util.Properties

val repoDir = file("offline-repo")

tasks.register("buildOfflineRepo") {

    group = "offline"
    description = "Build offline repo (FAST & STABLE)"

    doLast {

        println("========================================")
        println("STEP 1: Resolve dependencies (build)")
        println("========================================")

        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val command = if (isWindows) {
            listOf("cmd", "/c", "./gradlew", "build", "--refresh-dependencies", "--no-daemon")
        } else {
            listOf("./gradlew", "build", "--refresh-dependencies", "--no-daemon")
        }

        val process = ProcessBuilder(command)
            .directory(projectDir)
            .inheritIO()
            .start()

        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("Dependency resolution failed")
        }

        println("========================================")
        println("STEP 2: Copy artifacts")
        println("========================================")

        val cacheRoot = File(System.getProperty("user.home"))
            .resolve(".gradle/caches/modules-2/files-2.1")

        if (!cacheRoot.exists()) {
            throw GradleException("Gradle cache not found: $cacheRoot")
        }

        repoDir.deleteRecursively()
        repoDir.mkdirs()

        var count = 0

        cacheRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "pom") }
            .forEach { file ->

                val parts = file.absolutePath
                    .substringAfter("files-2.1${File.separator}")
                    .split(File.separator)

                if (parts.size >= 4) {

                    val group = parts[0]
                    val module = parts[1]
                    val version = parts[2]

                    val targetDir = repoDir
                        .resolve(group.replace(".", "/"))
                        .resolve(module)
                        .resolve(version)

                    targetDir.mkdirs()

                    file.copyTo(targetDir.resolve(file.name), overwrite = true)
                    count++
                }
            }

        println("Artifacts copied: $count")
        println("Repo: ${repoDir.absolutePath}")
    }
}

// -------------------------------
// Docker build
// -------------------------------
tasks.register("buildOfflineRepoImage") {

    dependsOn("buildOfflineRepo")

    doLast {

        println("========================================")
        println("Building Docker Image")
        println("========================================")

        val process = ProcessBuilder(
            "docker", "build",
            "-t", "offline-repo:latest",
            "."
        )
            .inheritIO()
            .start()

        val exit = process.waitFor()

        if (exit != 0) {
            throw GradleException("Docker build failed")
        }

        println(" Image ready: offline-repo:latest")
    }
}