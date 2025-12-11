# syntax=docker/dockerfile:1
# Multi-stage build: compile with Maven, then run with JRE

FROM maven:3.9.6-eclipse-temurin-8 AS build
WORKDIR /app
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV MAVEN_OPTS="-Xmx256m -XX:MaxRAMPercentage=40"

# Copy source
COPY pom.xml ./
COPY src ./src

# Build executable WAR (Spring Boot repackage) with constrained memory
RUN mvn -q -T 1C -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app

# Copy the built artifact
COPY --from=build /app/target/web-tracing-analysis.war ./web-tracing-analysis.war

# Default port
ENV SERVER_PORT=17001
EXPOSE 17001

# Allow passing datasource via environment
# Example:
#   SPRING_DATASOURCE_URL=jdbc:mysql://host:3306/db?useSSL=false&serverTimezone=UTC
#   SPRING_DATASOURCE_USERNAME=user
#   SPRING_DATASOURCE_PASSWORD=pass
#   SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

ENTRYPOINT ["java","-jar","web-tracing-analysis.war"]
