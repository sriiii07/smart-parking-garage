# Build the Java application and collect only its runtime dependencies.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=/app/target/lib

# Keep the JDK's jdk.httpserver module available; Maven and source remain in build stage.
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /app/target/smart-parking-garage-1.0-SNAPSHOT.jar /app/app.jar
COPY --from=build /app/target/lib /app/lib
ENV PORT=10000
EXPOSE 10000
CMD ["java", "-cp", "/app/app.jar:/app/lib/*", "com.smartparking.web.WebApplication"]
