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

// Downloads both JAR and POM for a given artifact coordinate
fun downloadArtifact(group: String, name: String, version: String, classifier: String? = null) {
    val groupPath = group.replace(".", "/")
    val baseUrl = "https://repo.maven.apache.org/maven2/$groupPath/$name/$version"
    val targetDir = File(offlineRepoDir.get().asFile, "$groupPath/$name/$version")
    targetDir.mkdirs()

    val suffix = if (classifier != null) "-$classifier" else ""

    val filesToFetch = listOf(
        "$name-$version$suffix.jar" to "$baseUrl/$name-$version$suffix.jar",
        "$name-$version.pom"        to "$baseUrl/$name-$version.pom",
        "$name-$version.module"     to "$baseUrl/$name-$version.module",  // Gradle module metadata
    )

    for ((fileName, url) in filesToFetch) {
        val target = File(targetDir, fileName)
        if (target.exists()) return  // skip if already present
        try {
            println("  Downloading $url")
            URL(url).openStream().use { it.copyTo(target.outputStream()) }
        } catch (_: Exception) {
            // .module files are optional; missing JARs/POMs will be printed below
            if (fileName.endsWith(".jar") || fileName.endsWith(".pom")) {
                println("  Could not fetch $url")
            }
        }
    }
}

// ----------------------------------
// CONFIGURATIONS
// ----------------------------------

// Runtime + compile deps for your actual application
val offlineDependencies: Configuration = configurations.create("offlineDependencies") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = true
}

// Gradle plugins — resolved separately because they live in a different coordinate space
val offlinePlugins: Configuration = configurations.create("offlinePlugins") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = true
}

dependencies {
    add("offlineDependencies", "com.squareup.okio:okio:3.6.0")
    // ── Application dependencies ──────────────────────────────────────────────
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-web:4.0.2")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-data-jpa:4.0.2")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-security:4.0.2")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-validation:4.0.2")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-data-redis:4.0.2")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-oauth2-resource-server:4.0.2")
    add("offlineDependencies", "com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre11")
    add("offlineDependencies", "org.springframework.boot:spring-boot-starter-test:4.0.2")

    add("offlineDependencies", "org.springframework.cloud:spring-cloud-dependencies:2025.0.1")
    add("offlineDependencies", "com.azure.spring:spring-cloud-azure-dependencies:6.1.0")

    // ── Additional application dependencies ───────────────────────────────────
    add("offlineDependencies", "org.apache.commons:commons-csv:1.10.0")
    add("offlineDependencies", "com.azure:azure-identity:1.12.1")
    add("offlineDependencies", "com.microsoft.graph:microsoft-graph:5.80.0")
    add("offlineDependencies", "com.squareup.okhttp3:okhttp:4.12.0")
    add("offlineDependencies", "org.projectlombok:lombok:1.18.32")
    add("offlineDependencies", "org.projectlombok:lombok:1.18.42")

    // ── Gradle plugins (resolved as plain Maven artifacts) ────────────────────
    add("offlinePlugins", "org.springframework.boot:spring-boot-gradle-plugin:4.0.2")
    add("offlinePlugins", "io.spring.gradle:dependency-management-plugin:1.1.6")
    add("offlinePlugins", "io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:1.1.6")
    add("offlinePlugins", "org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:5.1.0.4882")


    // Azure transitive deps
    add("offlineDependencies", "com.azure:azure-core:1.49.0")
    add("offlineDependencies", "com.azure:azure-core-http-netty:1.15.0")
    add("offlineDependencies", "com.microsoft.azure:msal4j:1.15.0")

    // Netty (pulled in by azure-core-http-netty and lettuce)
    add("offlineDependencies", "io.netty:netty-handler:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-handler-proxy:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-buffer:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-codec:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-codec-http:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-codec-http2:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-transport:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-transport-native-unix-common:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-transport-native-epoll:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-transport-native-kqueue:4.2.9.Final")
    add("offlineDependencies", "io.netty:netty-tcnative-boringssl-static:2.0.74.Final")
    add("offlineDependencies", "io.netty:netty-resolver-dns:4.2.9.Final")

    // Reactor
    add("offlineDependencies", "io.projectreactor:reactor-core:3.8.2")
    add("offlineDependencies", "io.projectreactor.netty:reactor-netty-http:1.3.2")

    // Jackson
    add("offlineDependencies", "com.fasterxml.jackson.core:jackson-core:2.20.2")
    add("offlineDependencies", "com.fasterxml.jackson.core:jackson-databind:2.20.2")
    add("offlineDependencies", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.2")

    // Kotlin (newer version pulled by okio-jvm)
    add("offlineDependencies", "org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
    add("offlineDependencies", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
    add("offlineDependencies", "org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21")

    add("offlineDependencies", "org.junit.jupiter:junit-jupiter:6.0.2")
    add("offlineDependencies", "org.junit.jupiter:junit-jupiter-api:6.0.2")
    add("offlineDependencies", "org.junit.jupiter:junit-jupiter-params:6.0.2")
    add("offlineDependencies", "org.junit.platform:junit-platform-commons:6.0.2")
    add("offlineDependencies", "org.mockito:mockito-core:5.20.0")
    add("offlineDependencies", "org.mockito:mockito-junit-jupiter:5.20.0")
    add("offlineDependencies", "org.assertj:assertj-core:3.27.6")
    add("offlineDependencies", "org.hamcrest:hamcrest:3.0")
    add("offlineDependencies", "org.awaitility:awaitility:4.3.0")
    add("offlineDependencies", "com.jayway.jsonpath:json-path:2.10.0")
    add("offlineDependencies", "jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    add("offlineDependencies", "net.bytebuddy:byte-buddy:1.17.8")
    add("offlineDependencies", "net.bytebuddy:byte-buddy-agent:1.17.8")
    add("offlineDependencies", "org.opentest4j:opentest4j:1.3.0")
    add("offlineDependencies", "org.apiguardian:apiguardian-api:1.1.2")
    add("offlineDependencies", "org.springframework:spring-test:7.0.3")

}

fun installFromCache(cacheRoot: File, repoRoot: File, jarFile: File) {
    val relative = jarFile.absolutePath
        .substringAfter("files-2.1${File.separator}")
        .split(File.separator)

    if (relative.size < 5) return

    val group   = relative[0]
    val name    = relative[1]
    val version = relative[2]
    val fileName = relative[4]

    val groupPath = group.replace(".", "/")
    val targetDir = File(repoRoot, "$groupPath/$name/$version")
    targetDir.mkdirs()

    val target = File(targetDir, fileName)
    
    // Skip if already exists — avoids Windows file lock error
    if (target.exists()) return

    jarFile.copyTo(target, overwrite = false)

    val pomInCache = File(jarFile.parentFile, "$name-$version.pom")
    if (pomInCache.exists()) {
        val pomTarget = File(targetDir, "$name-$version.pom")
        if (!pomTarget.exists()) {
            pomInCache.copyTo(pomTarget, overwrite = false)
        }
    }
}

// ----------------------------------
// MAIN TASK
// ----------------------------------
tasks.register("buildOfflineRepo") {
    dependsOn(offlineDependencies, offlinePlugins)   // force resolution before doLast

    doLast {
        val repoRoot   = offlineRepoDir.get().asFile
        val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")

        // 1. Resolve both configurations so Gradle populates the cache
        println(" Resolving application dependencies…")
        offlineDependencies.resolve()
        println(" Resolving plugin dependencies…")
        offlinePlugins.resolve()

        // 2. Walk the Gradle cache and install every resolved artifact
        // Copies ALL file types: jar, aar, pom, module — so nothing is missed
        println(" Copying artifacts from Gradle cache…")
        if (gradleCache.exists()) {
            gradleCache.walkTopDown()
                .filter { it.isFile && it.extension in listOf("jar", "aar", "pom", "module") }
                .forEach { installFromCache(gradleCache, repoRoot, it) }
        } else {
            println("  Gradle cache not found at $gradleCache")
        }

        // 3. Kotlin stdlib — Gradle itself uses this; inject it explicitly
        println(" Injecting Kotlin stdlib…")
        downloadArtifact("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.25")

        // 4. Emit a simple repo index for debugging
        val allFiles = repoRoot.walkTopDown().filter { it.isFile }.toList()
        println(" Offline repo ready — ${allFiles.size} files in $repoRoot")
    }
}
