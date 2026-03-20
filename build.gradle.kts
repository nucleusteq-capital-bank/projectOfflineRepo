import java.io.File
import java.util.Properties

// -------------------------------
// Load projects list
// -------------------------------
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

// -------------------------------
// TASK 1: Build Offline Repo
// -------------------------------
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

            val wrapper = if (isWindows) {
                File(projectDir, "gradlew.bat")
            } else {
                File(projectDir, "gradlew")
            }

            if (!wrapper.exists()) {
                throw GradleException("Missing gradlew in: ${projectDir.absolutePath}")
            }

            // -------------------------------
            // STEP 1A: dependencies
            // -------------------------------
            val pb1 = if (isWindows) {
                ProcessBuilder(
                    "cmd", "/c",
                    wrapper.absolutePath,
                    "dependencies",
                    "--refresh-dependencies",
                    "--no-daemon",
                    "--console=plain"
                )
            } else {
                ProcessBuilder(
                    wrapper.absolutePath,
                    "dependencies",
                    "--refresh-dependencies",
                    "--no-daemon",
                    "--console=plain"
                )
            }

            pb1.environment()["CI"] = "true"

            val process1 = pb1
                .directory(projectDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()

            val exit1 = process1.waitFor()

            if (exit1 != 0) {
                throw GradleException("Dependency resolution failed: $path")
            }

            // -------------------------------
            // STEP 1B: buildEnvironment
            // -------------------------------
            println("Resolving buildscript deps: $path")

            val pb2 = if (isWindows) {
                ProcessBuilder(
                    "cmd", "/c",
                    wrapper.absolutePath,
                    "buildEnvironment",
                    "--refresh-dependencies",
                    "--no-daemon",
                    "--console=plain"
                )
            } else {
                ProcessBuilder(
                    wrapper.absolutePath,
                    "buildEnvironment",
                    "--refresh-dependencies",
                    "--no-daemon",
                    "--console=plain"
                )
            }

            pb2.environment()["CI"] = "true"

            val process2 = pb2
                .directory(projectDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()

            val exit2 = process2.waitFor()

            if (exit2 != 0) {
                throw GradleException("Buildscript dependency resolution failed: $path")
            }
        }

        // -------------------------------
        // STEP 2: Copy cache → offline repo
        // -------------------------------
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

                val relativePath = file.absolutePath.substringAfter("files-2.1${File.separator}")
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

                    file.copyTo(
                        targetDir.resolve(file.name),
                        overwrite = true
                    )

                    count++
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

// -------------------------------
// TASK 2: Build Docker Image
// -------------------------------
tasks.register("buildOfflineRepoImage") {

    group = "docker"
    description = "Build Docker image for offline repo"

    dependsOn("buildOfflineRepo")

    doLast {

        println("========================================")
        println("STEP 3: Build Docker Image")
        println("========================================")

        val pb = ProcessBuilder(
            "docker", "build",
            "-t", "offline-repo:latest",
            "."
        )

        pb.environment()["CI"] = "true"

        val process = pb
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()

        val exit = process.waitFor()

        if (exit != 0) {
            throw GradleException("Docker build failed")
        }

        println("========================================")
        println("DOCKER IMAGE READY: offline-repo:latest")
        println("========================================")
    }
}