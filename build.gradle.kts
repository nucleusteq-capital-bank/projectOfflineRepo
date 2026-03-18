import java.io.File
import java.util.Properties

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

tasks.register("buildOfflineRepo") {

    group = "offline"
    description = "Build offline repo from project dependency resolution"

    doLast {

        println("========================================")
        println("STEP 1: Resolve dependencies")
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
                    "build",
                    "--refresh-dependencies"
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
                    "build",
                    "--refresh-dependencies"
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
                    val hashDir = segments[index + 4] // important

                    val targetDir = repoDir
                        .resolve(group.replace(".", "/"))
                        .resolve(module)
                        .resolve(version)

                    targetDir.mkdirs()

                    val targetFile = targetDir.resolve(file.name)

                    // Avoid duplicates from multiple hash folders
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