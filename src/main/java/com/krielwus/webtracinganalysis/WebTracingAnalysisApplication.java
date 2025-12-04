package com.krielwus.webtracinganalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。负责启动 Web 埋点监控与分析平台的 Spring Boot 应用，
 * 初始化 Web 层、数据持久化、以及各类业务组件。
 */
@SpringBootApplication
public class WebTracingAnalysisApplication {

    /**
     * 启动方法。加载 Spring 应用上下文并打开嵌入式 Web 容器。
     * @param args 启动参数
     */
	public static void main(String[] args) {
		SpringApplication.run(WebTracingAnalysisApplication.class, args);
	}

}
