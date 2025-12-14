# 第一阶段：使用Maven镜像构建应用
FROM maven:3.8.6-openjdk-8-slim AS builder
WORKDIR /app
# 先复制POM文件，利用Docker缓存层下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 复制源代码并打包
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：创建轻量级运行时镜像
FROM openjdk:8-jre-alpine
# 设置工作目录
WORKDIR /app
# 创建非root用户运行应用以增强安全性
RUN addgroup -S springboot && adduser -S springboot -G springboot
USER springboot
# 从构建阶段复制打包好的WAR文件
COPY --from=builder /app/target/web-tracing-analysis.war app.war
# 暴露端口
EXPOSE 8080
# 设置健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
# 启动应用，优化JVM参数以适配容器环境
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "-Djava.security.egd=file:/dev/./urandom", "app.war"]