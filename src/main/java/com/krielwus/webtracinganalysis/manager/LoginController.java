package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;

/**
 * 登录接口控制器。
 * 负责接收登录请求、校验验证码与用户凭证，并在成功后写入会话登录态。
 */
@Controller
@RequestMapping("/api")
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录接口：读取请求体 JSON，校验验证码与用户名密码（演示环境使用固定账号），
     * 成功后返回首页跳转地址并写入会话。
     */
    @PostMapping("/login")
    @ResponseBody
    public ResultInfo login(@RequestBody JSONObject jsonObject, HttpSession session) {
        String username = String.valueOf(jsonObject.getOrDefault("username", "")).trim();
        String password = String.valueOf(jsonObject.getOrDefault("password", "")).trim();
        // String verifyCode = String.valueOf(jsonObject.getOrDefault("verifyCode", "")).trim();
        // if (username.isEmpty() || password.isEmpty() || verifyCode.isEmpty()) {
        //     return new ResultInfo(400, "参数不完整");
        // }
        if (username.isEmpty() || password.isEmpty()) {
            return new ResultInfo(400, "参数不完整");
        }

        // String captcha = (String) session.getAttribute("captcha");
        // if (captcha == null || ""==captcha){
        //     return new ResultInfo(400, "验证码已过期");
        // }
        // if (captcha != null && captcha.equalsIgnoreCase(verifyCode)) {
        boolean ok = userService.authenticate(username, password);
        if (ok) {
            UserAccount user = userService.findByUsername(username);
            if (user != null) {
                session.setAttribute("user", user);
                session.setAttribute("userId", user.getId());
                session.setAttribute("username", user.getUsername());
                session.setAttribute("role", user.getRole());
            } else {
                session.setAttribute("username", username);
            }
            return new ResultInfo(200, "登录成功", "","./index.html");
        } else {
            return new ResultInfo(400, "用户名或密码错误");
        }
    }

    @PostMapping("/logout")
    @ResponseBody
    public ResultInfo logout(HttpSession session) {
        try {
            session.invalidate();
        } catch (Exception ignore) {
        }
        return new ResultInfo(200, "退出成功");
    }

    @PostMapping("/register")
    @ResponseBody
    public ResultInfo register(@RequestBody JSONObject jsonObject) {
        String username = String.valueOf(jsonObject.getOrDefault("username", "")).trim();
        String password = String.valueOf(jsonObject.getOrDefault("password", "")).trim();
        if (username.isEmpty() || password.isEmpty()) {
            return new ResultInfo(400, "参数不完整");
        }
        boolean ok = userService.register(username, password);
        if (ok) {
            return new ResultInfo(200, "注册成功");
        } else {
            return new ResultInfo(400, "注册失败，用户名存在或参数不合法");
        }
    }


}
