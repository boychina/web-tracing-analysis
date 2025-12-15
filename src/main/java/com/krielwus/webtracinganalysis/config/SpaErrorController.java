package com.krielwus.webtracinganalysis.config;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

/**
 * SPA 错误处理：
 * 对于返回 404 的 HTML 请求，统一转发到 index.html，
 * 让前端路由接管；其他情况保留默认错误处理。
 */
@Controller
public class SpaErrorController implements ErrorController {

    @Override
    public String getErrorPath() {
        return "/error";
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        return "index";
    }
}
