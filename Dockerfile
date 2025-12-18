# 第一阶段：构建 (Build Stage)
FROM maven:3.8.6-openjdk-8-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# 跳过测试以加快构建速度
RUN mvn clean package -DskipTests

# 第二阶段：运行 (Run Stage)
FROM openjdk:8-jre-alpine
WORKDIR /app
# 从构建阶段复制生成的 JAR 包
COPY --from=build /app/target/web-tracing-analysis.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
