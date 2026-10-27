FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon clean build

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/dummy-api-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
