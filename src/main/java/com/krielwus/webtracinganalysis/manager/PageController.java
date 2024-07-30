package com.krielwus.webtracinganalysis.manager;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class PageController {

    @RequestMapping("/")
    @ResponseBody
    public void page(HttpServletRequest request, HttpSession session, HttpServletResponse response) throws IOException {
        if (StrUtil.isEmptyIfStr(session.getAttribute("username"))){
            response.sendRedirect("/login.html");
        }else {
            response.sendRedirect("/index.html");
        }
    }

    @RequestMapping("/login.html")
    public String first() {
        return "login";
    }

    //进入登录页
//    @GetMapping("/login")
//    public String toLogin() {
//        return "login.html";
//    }

    //进入首页
    @RequestMapping("/index.html")
    public String toIndex() {
        return "index";
    }

}
