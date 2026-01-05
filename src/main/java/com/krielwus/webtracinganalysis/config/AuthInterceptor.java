package com.krielwus.webtracinganalysis.config;

import com.krielwus.webtracinganalysis.entity.UserAccount;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (isLoggedIn(session)) {
            return true;
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"unauthorized\"}");
        return false;
    }

    private boolean isLoggedIn(HttpSession session) {
        if (session == null) return false;
        Object u = session.getAttribute("user");
        if (u instanceof UserAccount) return true;
        String name = String.valueOf(session.getAttribute("username"));
        return name != null && !name.trim().isEmpty();
    }
}
