# 第一阶段：构建
# FROM maven:3.9-amazoncorretto-17-debian AS build
FROM registry.cn-hangzhou.aliyuncs.com/acs/maven:3.9-amazoncorretto-17-debian AS build

WORKDIR /app

# 复制 Maven 配置（关键！）
COPY settings.xml /root/.m2/settings.xml

COPY pom.xml .
COPY src ./src
# 跳过测试以加快构建速度
RUN mvn clean package -DskipTests

# 第二阶段：运行
# FROM eclipse-temurin:17-jre-alpine-3.23
FROM registry.cn-hangzhou.aliyuncs.com/acs/eclipse-temurin:17-jre-alpine AS build-runtime

WORKDIR /app
# 从构建阶段复制生成的 JAR 包
COPY --from=build /app/target/web-tracing-analysis.jar app.jar
EXPOSE 17001
ENTRYPOINT ["java", "-jar", "app.jar"]
