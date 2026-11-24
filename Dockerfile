# File:        Dockerfile
# Module:      Deployment
# Purpose:     Builds the application and runs it on Java 21 in a small container for Render.
# Responsible: Felix Joshua H. Abing
# Created:     2026-11-24

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -q -B -Dmaven.test.skip=true -Djacoco.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pa-ayo-1.0.0.jar app.jar
# Not root; the numeric group lets the process read Render's secret files
USER 1000:1000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
CMD ["java", "-jar", "app.jar"]
