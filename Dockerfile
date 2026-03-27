FROM gradle:8.12-jdk21 AS downloader

# Override repos to use Maven Central during download
RUN mkdir -p /root/.gradle/init.d && cat > /root/.gradle/init.d/override-repos.gradle << 'EOF'
allprojects {
    buildscript {
        repositories {
            mavenCentral()
            maven { url 'https://plugins.gradle.org/m2' }
        }
    }
    repositories {
        mavenCentral()
        maven { url 'https://plugins.gradle.org/m2' }
        maven { url 'https://repo.maven.apache.org/maven2' }
    }
}
EOF

# Copy full backend project
COPY projectFiles/backend/ /download/backend/

# Copy full appexperience project
COPY projectFiles/appexperience/ /download/appexperience/

# Build both projects to force download of all JARs
RUN cd /download/backend && gradle clean build -x test --no-daemon || true
RUN cd /download/appexperience && gradle clean build -x test --no-daemon || true

# Reorganize Gradle cache into Maven layout
RUN find /root/.gradle/caches/modules-2/files-2.1 \( -name "*.jar" -o -name "*.pom" \) | while read f; do \
      group=$(echo "$f" | awk -F'files-2.1/' '{print $2}' | cut -d'/' -f1 | tr '.' '/'); \
      artifact=$(echo "$f" | awk -F'files-2.1/' '{print $2}' | cut -d'/' -f2); \
      version=$(echo "$f" | awk -F'files-2.1/' '{print $2}' | cut -d'/' -f3); \
      filename=$(basename "$f"); \
      mkdir -p "/maven-repo/$group/$artifact/$version"; \
      cp "$f" "/maven-repo/$group/$artifact/$version/"; \
    done

FROM nginx:alpine
COPY --from=downloader /maven-repo/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80