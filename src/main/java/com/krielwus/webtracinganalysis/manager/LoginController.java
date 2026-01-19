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

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.krielwus.webtracinganalysis.service.AuthTokenService;
import com.krielwus.webtracinganalysis.info.Tokens;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.Cookie;

/**
 * 登录接口控制器。
 * 负责接收登录请求、校验验证码与用户凭证，并在成功后写入会话登录态。
 */
@Controller
@RequestMapping("/api")
public class LoginController {

    private final UserService userService;
    private final AuthTokenService tokenService;
    @Value("${COOKIE_DOMAIN:}")
    private String cookieDomain;
    @Value("${REFRESH_TOKEN_TTL_DAYS:30}")
    private long refreshTtlDays;

    public LoginController(UserService userService, AuthTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    /**
     * 登录接口：读取请求体 JSON，校验验证码与用户名密码（演示环境使用固定账号），
     * 成功后返回首页跳转地址并写入会话。
     */
    @PostMapping("/login")
    @ResponseBody
    public ResultInfo login(@RequestBody JSONObject jsonObject, HttpSession session, HttpServletRequest request, HttpServletResponse response) {
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
            String deviceId = request.getHeader("X-Device-Id");
            if (deviceId == null || deviceId.trim().isEmpty()) {
                String ua = request.getHeader("User-Agent");
                deviceId = Integer.toHexString((ua == null ? "unknown" : ua).hashCode());
            }
            Tokens tokens = tokenService.issueTokens(user, deviceId, remoteIp(request), request.getHeader("User-Agent"));
            setRefreshCookie(response, tokens.getRefreshToken());
            JSONObject data = new JSONObject();
            data.put("accessToken", tokens.getAccessToken());
            data.put("redirect", "./index.html");
            return new ResultInfo(1000, "success", data);
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

    private String remoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            int idx = ip.indexOf(',');
            if (idx > 0) ip = ip.substring(0, idx);
            return ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return request.getRemoteAddr();
    }

    private void setRefreshCookie(HttpServletResponse response, String rtPlain) {
        if (rtPlain == null) return;
        String encoded;
        try {
            encoded = java.net.URLEncoder.encode(rtPlain, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            encoded = rtPlain;
        }
        Cookie c = new Cookie("RT", encoded);
        c.setHttpOnly(true);
        c.setSecure(false);
        c.setPath("/");
        if (cookieDomain != null && !cookieDomain.isEmpty()) c.setDomain(cookieDomain);
        c.setMaxAge((int)(refreshTtlDays * 24 * 60 * 60));
        response.addCookie(c);
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
