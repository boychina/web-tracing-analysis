package com.krielwus.webtracinganalysis.config;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.service.UserService;
import com.krielwus.webtracinganalysis.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public JwtAuthInterceptor(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (isLoggedIn(session)) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            try {
                DecodedJWT jwt = jwtUtil.verify(token);
                Long userId = Long.valueOf(jwt.getSubject());
                String username = jwt.getClaim("username").asString();
                String role = jwt.getClaim("role").asString();
                UserAccount user = userService.findById(userId);
                if (user == null || !user.getUsername().equals(username)) {
                    return unauthorized(response);
                }
                HttpSession s = request.getSession(true);
                s.setAttribute("user", user);
                s.setAttribute("userId", user.getId());
                s.setAttribute("username", user.getUsername());
                s.setAttribute("role", user.getRole());
                return true;
            } catch (Exception e) {
                return unauthorized(response);
            }
        }
        return unauthorized(response);
    }

    private boolean isLoggedIn(HttpSession session) {
        if (session == null) return false;
        Object u = session.getAttribute("user");
        if (u instanceof UserAccount) return true;
        String name = String.valueOf(session.getAttribute("username"));
        return name != null && !name.trim().isEmpty();
    }

    private boolean unauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"unauthorized\"}");
        return false;
    }
}
