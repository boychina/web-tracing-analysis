package com.krielwus.webtracinganalysis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用上下文加载测试。
 * 用于验证 Spring Boot 启动与 Bean 装配的基本健康状态。
 */
@SpringBootTest
class WebTracingAnalysisApplicationTests {

    /** 启动上下文，不做断言（失败将抛出异常） */
    @Test
    void contextLoads() {
    }

}
