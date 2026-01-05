package com.krielwus.webtracinganalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @ClassName WebMvcConfig
 * @Description 跨域请求配置
 * @Version 1.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    /**
     * 配置全局跨域策略，使前端监控与管理页面可直接调用后端接口。
     * 包含通配路径与特定前缀路径两类配置，允许常见 HTTP 方法与全部头部。
     * @param registry CORS 注册器
     */
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("**");
        registry.addMapping("/cros/**")
                .allowCredentials(true)
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("**");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns(
                        "/api/user/**",
                        "/api/application/**",
                        "/api/getAllTracingList",
                        "/api/getBaseInfo",
                        "/api/cleanTracingList",
                        "/user/**",
                        "/application/**",
                        "/getAllTracingList",
                        "/getBaseInfo",
                        "/cleanTracingList"
                )
                .excludePathPatterns(
                        "/api/login",
                        "/api/register",
                        "/api/captcha/**",
                        "/api/trackweb",
                        "/login",
                        "/register",
                        "/captcha/**",
                        "/trackweb"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/templates/assets/");
    }

}
