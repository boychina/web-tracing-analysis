package com.krielwus.webtracinganalysis.manager;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * 页面路由控制器。
 * 根据登录态跳转到登录页或首页，并提供模板视图的映射。
 */
@Controller
public class PageController {

    /**
     * 根路径：根据 Session 是否存在用户名决定跳转到登录页或首页。
     */
    @RequestMapping("/")
    @ResponseBody
    public void page(HttpServletRequest request, HttpSession session, HttpServletResponse response) throws IOException {
        if (StrUtil.isEmptyIfStr(session.getAttribute("user")) && StrUtil.isEmptyIfStr(session.getAttribute("username"))){
            response.sendRedirect("/login.html");
        }else {
            response.sendRedirect("/index.html");
        }
    }

    /** 登录页模板 */
    @RequestMapping("/login.html")
    public String first() {
        return "login";
    }

    //进入登录页
//    @GetMapping("/login")
//    public String toLogin() {
//        return "login.html";
//    }

    /** 首页模板 */
    @RequestMapping("/index.html")
    public String toIndex() {
        return "index";
    }

}
