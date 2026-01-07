# Web Tracing Analysis

基于 [web-tracing](https://github.com/M-cheng-web/web-tracing) 的前端观测与分析平台。

- **GitHub 仓库**: [boychina/web-tracing-analysis](https://github.com/boychina/web-tracing-analysis)
- **前端 SDK 文档**: [web-tracing Docs](https://m-cheng-web.github.io/web-tracing/)

## ✨ 镜像功能

- **全链路监控**：性能、异常、请求、资源、路由、曝光、录屏与行为追踪
- **开箱即用**：自动初始化数据库，包含大屏与管理后台
- **轻量部署**：配合 MySQL 即可完整运行

### 界面预览
<img src="https://raw.githubusercontent.com/boychina/web-tracing-analysis/main/src/main/doc/1722353141613.jpg" />
<img src="https://raw.githubusercontent.com/boychina/web-tracing-analysis/main/src/main/doc/1722353211528.jpg" />
<img src="https://raw.githubusercontent.com/boychina/web-tracing-analysis/main/src/main/doc/1722352544969.jpg" />

### 应用注册数据上报
<img src="https://raw.githubusercontent.com/boychina/web-tracing-analysis/main/docs/usage-process.png" />

## 🚀 快速开始 (Quick Start)

拉取镜像
```bash
docker pull boychina/web-tracing-analysis:latest
```

无需任何配置，复制以下命令即可一键启动完整服务（包含 MySQL 数据库）：

```bash
docker network create web-tracing-net || true && \
docker run -d --name wta-mysql --network web-tracing-net -p 3307:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:9.5.0 && \
echo "Waiting for MySQL to start..." && sleep 15 && \
docker run -d --name web-tracing-analysis --network web-tracing-net -p 17001:17001 \
  -e SERVER_PORT=17001 \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=123456 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://wta-mysql:3306/web_tracing?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true" \
  boychina/web-tracing-analysis:latest
```

启动成功后访问：
- 地址：[http://localhost:17001](http://localhost:17001)
- 账号：`admin`
- 密码：`admin`

## 📦 镜像信息

- **镜像地址**: `boychina/web-tracing-analysis:latest`
- **端口**: `17001`
- **依赖**: 需要连接 MySQL 9.5.0 数据库

## docker-compose 示例
```yml
version: '3'
services:
  mysql:
    image: mysql:9.5.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
    ports:
      - "3307:3306"  # 建议改用3307，避免与系统MySQL冲突
    volumes:
      - mysql-data:/var/lib/mysql  # 添加数据卷，避免数据丢失

  web-tracing-analysis:
    build: .
    image: web-tracing-analysis
    ports:
      - "17001:17001"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/web_tracing?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true&useServerPrepStmts=true&cachePrepStmts=true&useCursorFetch=true&defaultFetchSize=1000&allowPublicKeyRetrieval=true
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=123456
      - SERVER_PORT=17001
    depends_on:
      - mysql  # 确保MySQL先启动

volumes:
  mysql-data:
```