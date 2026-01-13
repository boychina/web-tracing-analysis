package com.krielwus.webtracinganalysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
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

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 使用 allowedOriginPatterns 代替 allowedOrigins 以支持 allowCredentials(true)
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("*");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
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
