package com.krielwus.webtracinganalysis;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 传统 Servlet 容器部署入口。
 * 当以 WAR 方式部署到外部容器（如 Tomcat）时，
 * 通过该初始化器引导 Spring Boot 应用。
 */
public class ServletInitializer extends SpringBootServletInitializer {

	/**
	 * 指定应用主类用于外部容器启动。
	 * @param application 构建器
	 * @return 构建结果
	 */
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(WebTracingAnalysisApplication.class);
	}

}
