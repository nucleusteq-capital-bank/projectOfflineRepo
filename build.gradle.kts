import java.io.File
import java.util.Properties

// ---------------- LOAD PROJECT PATHS ----------------

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

// ---------------- OUTPUT ----------------

val repoDir = file("offline-repo")

// ---------------- TASK ----------------

tasks.register("buildOfflineRepo") {

    group = "offline"
    description = "Build offline repo from project dependency resolution"

    doLast {

        println("========================================")
        println("STEP 1: Resolve dependencies")
        println("========================================")

        projectPaths.forEach { path ->

            val projectDir = file(path)

            if (!projectDir.exists()) {
                throw GradleException("Project path not found: $path")
            }

            println("➡ Resolving: $path")

            val gradleCmd = "gradlew.bat"

            val process = ProcessBuilder(
                gradleCmd,
                "help",
                "--refresh-dependencies"
            )
                .directory(projectDir)
                .inheritIO()
                .start()

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