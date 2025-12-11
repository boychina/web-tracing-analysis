## 简介

基于[web-tracing](https://github.com/M-cheng-web/web-tracing)的前端分析项目，监控web前端项目性能，异常，请求，资源，路由，曝光，录屏等以及行为追踪分析。
项目基于springBoot框架，使用maven构建,这里仅做分析展示的一种形式。
项目仅实现了简单的数据大屏分析，仅做演示分析，不包含任何商业商业价值。仅做了简单的用户行为追踪。登陆，鉴权，菜单等都是写的静态数据，仅做演示分析。
项目使用构建环境：
- IDE ： IntelliJ IDEA
- JDK ： 1.8
- MAVEN ： 3.6
- SPRINGBOOT ： 2.1.11.RELEASE

### 系统体验
git下载项目，使用maven构建项目，然后使用idea打开项目，运行项目，[本地运行访问地址](http://127.0.0.1:17001/) http://127.0.0.1:17001/

| 账号    | 密码 |
|-------| ------- |
| admin | admin |

###

<img src="src/main/doc/1722353141613.jpg">
<img src="src/main/doc/1722353211528.jpg">
<img src="src/main/doc/1722352544969.jpg">

### 压力测试

使用服务端压测端点 `/stressRun` 进行并发压测，结果如下：

| 指标 | 数值 |
| ---- | ---- |
| 并发用户数 | 100 |
| 循环次数 | 5000 |
| 成功数 | 4603 |
| 失败数 | 397 |
| 总耗时(ms) | 6071 |
| QPS | 758.19 |
| p50 响应时间(ms) | 90 |
| p90 响应时间(ms) | 181 |
| p99 响应时间(ms) | 252 |

### 容器化部署

- 构建镜像
  - `docker build -t web-tracing-analysis:latest .`

- 运行容器（示例：映射端口与数据库环境变量）
  - `docker run -d --name web-tracing-analysis -p 17001:17001 \
    -e SPRING_DATASOURCE_URL="jdbc:mysql://<db_host>:3306/<db_name>?useSSL=false&serverTimezone=UTC" \
    -e SPRING_DATASOURCE_USERNAME="<db_user>" \
    -e SPRING_DATASOURCE_PASSWORD="<db_pass>" \
    -e SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver" \
    -e SERVER_PORT=17001 \
    web-tracing-analysis:latest`
  - macOS 连接宿主机数据库请使用 `host.docker.internal`：
    - `SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/<db_name>?useSSL=false&serverTimezone=UTC`

- 使用 `.env` 文件（可选）
  - 将数据库配置写入 `.env`，然后：
  - `docker run -d --name web-tracing-analysis --env-file .env -p 17001:17001 web-tracing-analysis:latest`

- 访问应用
  - `http://127.0.0.1:17001/`

- 运行时可调配置（示例）
  - `-e TRACING_INGEST_CONSUMER_THREADS=4` 增加异步消费者线程
  - `-e TRACING_INGEST_BATCH_SIZE=200` 调整批量入库大小
  - `-e SPRING_DATASOURCE_URL="...&rewriteBatchedStatements=true"` 提升 JDBC 批处理效率（MySQL）

#### 使用 Docker Compose 一键部署（推荐）

- 预先构建运行镜像（已在本地完成 WAR 编译）：
  - `docker build -f Dockerfile.runtime -t web-tracing-analysis:latest .`
- 启动 MySQL 与应用：
  - `docker compose up -d`
- 配置说明：
  - `docker-compose.yml` 中应用默认连接 `mysql` 服务（服务名），数据库为 `web_tracing`
  - 默认数据库 root 密码取自环境 `SPRING_DATASOURCE_PASSWORD`（未设置时为 `CHANGE_ME`）
  - 如需修改端口或参数，调整 `docker-compose.yml` 的 `ports` 与 `environment`
- 验证：
  - `docker compose ps` 查看服务状态
  - `docker logs -f webtracing-app` 查看应用日志
  - 浏览器访问 `http://127.0.0.1:17001/`

## 许可证

本项目采用Apache License 2.0许可。详情参见[LICENSE](LICENSE)文件。

致谢
===============
- [web-tracing](https://github.com/M-cheng-web/web-tracing) 为前端项目提供【 埋点、行为、性能、异常、请求、资源、路由、曝光、录屏 】监控手段。web-tracing文档地址：https://m-cheng-web.github.io/web-tracing/
- [Pear Admin Layui](https://gitee.com/pear-admin/pear-admin-layui) Pear Admin 是一款开箱即用的前端开发模板，提供便捷快速的开发方式，延续 Admin 的设计规范。Pear Admin Layui文档地址：http://www.pearadmin.com/doc/
- [hutool](https://gitee.com/dromara/hutool) 优秀的，开源的，小而全的Java工具类库，使Java拥有函数式语言般的优雅，让Java语言也可以“甜甜的”。
- [JetBrains Open Source](https://www.jetbrains.com/zh-cn/opensource/?from=archery) 为项目提
---
