import java.io.File
import java.util.Properties

// ==============================
// Load projects from properties
// ==============================
val props = Properties()
file("projects.properties").inputStream().use { props.load(it) }

val projectPaths = props.getProperty("projects")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

if (projectPaths.isEmpty()) {
    throw GradleException("No projects defined in projects.properties")
}

println("Projects loaded: $projectPaths")

val repoDir = file("offline-repo")

// ==============================
// Main Task
// ==============================
tasks.register("buildOfflineRepo") {

    group = "offline"
    description = "Build offline repo from project dependency resolution"

    doLast {

        println("========================================")
        println("STEP 1: Force dependency resolution")
        println("========================================")

        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        projectPaths.forEach { path ->

            val projectDir = file(path)

            if (!projectDir.exists()) {
                throw GradleException("Project path not found: $path")
            }

            println("Resolving: $path")

            val process = if (isWindows) {
                val wrapper = File(projectDir, "gradlew.bat")
                if (!wrapper.exists()) {
                    throw GradleException("Missing gradlew.bat in: ${projectDir.absolutePath}")
                }

                ProcessBuilder(
                    "cmd", "/c",
                    wrapper.absolutePath,
                    "clean",
                    "build",
                    "--refresh-dependencies",
                    "--no-build-cache"
                )
                    .directory(projectDir)
                    .inheritIO()
                    .start()

            } else {
                val wrapper = File(projectDir, "gradlew")
                if (!wrapper.exists()) {
                    throw GradleException("Missing gradlew in: ${projectDir.absolutePath}")
                }

                ProcessBuilder(
                    wrapper.absolutePath,
                    "clean",
                    "build",
                    "--refresh-dependencies",
                    "--no-build-cache"
                )
                    .directory(projectDir)
                    .inheritIO()
                    .start()
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw GradleException("Gradle failed for project: $path")
            }
        }

        println("========================================")
        println("STEP 2: Copy Gradle cache → offline repo")
        println("========================================")

        val cacheRoot = File(System.getProperty("user.home"))
            .resolve(".gradle/caches/modules-2/files-2.1")

        if (!cacheRoot.exists()) {
            throw GradleException("Gradle cache not found: $cacheRoot")
        }

        var count = 0

        cacheRoot.walkTopDown().forEach { file ->

            if (file.isFile && (file.name.endsWith(".jar") || file.name.endsWith(".pom"))) {

                val segments = file.toPath().toString().split(File.separator)
                val index = segments.indexOf("files-2.1")

                if (index != -1 && segments.size > index + 4) {

                    val group = segments[index + 1]
                    val module = segments[index + 2]
                    val version = segments[index + 3]

                    val targetDir = repoDir
                        .resolve(group.replace(".", "/"))
                        .resolve(module)
                        .resolve(version)

                    targetDir.mkdirs()

                    val targetFile = targetDir.resolve(file.name)

                    // Avoid duplicates (multiple hash dirs)
                    if (!targetFile.exists()) {
                        file.copyTo(targetFile)
                        count++
                    }
                }
            }
        }

        println("========================================")
        println("OFFLINE REPO READY")
        println("Artifacts copied: $count")
        println("Location: ${repoDir.absolutePath}")
        println("========================================")
    }
}

tasks.register("buildOfflineRepoImage") {

    group = "offline"
    description = "Build Docker image for offline Maven repository"

    dependsOn("buildOfflineRepo")

    doLast {

        val repoDir = file("offline-repo")
        if (!repoDir.exists() || repoDir.listFiles()?.isEmpty() == true) {
            throw GradleException("offline-repo is missing or empty. Run buildOfflineRepo first.")
        }

        val dockerfile = file("Dockerfile")

        if (!dockerfile.exists()) {
            throw GradleException("Dockerfile not found in project root.")
        }

        println("========================================")
        println("STEP 3: Build Docker Image")
        println("========================================")

        val process = ProcessBuilder(
            "docker",
            "build",
            "-t",
            "offline-maven-repo:latest",
            "."
        )
            .inheritIO()
            .start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw GradleException("Docker build failed")
        }

        println("========================================")
        println("DOCKER IMAGE READY")
        println("Image: offline-maven-repo:latest")
        println("========================================")
    }
}

tasks.register("runOfflineRepoImage") {

    group = "offline"
    description = "Run offline Maven repo Docker container"

    doLast {

        println("========================================")
        println("STEP 4: Run Docker Container")
        println("========================================")

        val process = ProcessBuilder(
            "docker",
            "run",
            "-d",
            "-p",
            "8081:8081",
            "--name",
            "offline-maven-repo",
            "offline-maven-repo:latest"
        )
            .inheritIO()
            .start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            println("⚠ Container may already exist. Trying restart...")

            ProcessBuilder("docker", "start", "offline-maven-repo")
                .inheritIO()
                .start()
                .waitFor()
        }

        println("========================================")
        println("CONTAINER RUNNING at http://localhost:8081")
        println("========================================")
    }
}