package com.krielwus.webtracinganalysis.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @ClassName WebMvcConfig
 * @Description 跨域请求配置
 * @Version 1.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final JwtAuthInterceptor jwtAuthInterceptor;
    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> privateNetworkAccessFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain filterChain)
                    throws ServletException, IOException {
                if ("true".equalsIgnoreCase(request.getHeader("Access-Control-Request-Private-Network"))) {
                    response.setHeader("Access-Control-Allow-Private-Network", "true");
                }
                filterChain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

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
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/user/**",
                        "/api/application/**",
                        "/api/webTrack/**",
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
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/sso/**",
                        "/api/auth/logout",
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
