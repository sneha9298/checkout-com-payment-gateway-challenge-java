# Build stage: compile and package the app with the Gradle wrapper.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
# Copy the build files first so the dependency download is cached between builds.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
# Tests run in CI, not in the image build.
RUN ./gradlew --no-daemon bootJar

# Runtime stage: JRE only, no build tools or source code.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
