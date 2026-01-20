package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.krielwus.webtracinganalysis.entity.RefreshToken;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.info.Tokens;
import com.krielwus.webtracinganalysis.repository.RefreshTokenRepository;
import com.krielwus.webtracinganalysis.service.AuthTokenService;
import com.krielwus.webtracinganalysis.service.UserService;
import com.krielwus.webtracinganalysis.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthTokenService tokenService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository rtRepo;
    @Value("${COOKIE_DOMAIN:}")
    private String cookieDomain;
    @Value("${REFRESH_TOKEN_TTL_DAYS:30}")
    private long refreshTtlDays;

    public AuthController(UserService userService, AuthTokenService tokenService, JwtUtil jwtUtil, RefreshTokenRepository rtRepo) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.jwtUtil = jwtUtil;
        this.rtRepo = rtRepo;
    }

    @PostMapping("/login")
    public ResultInfo tokenLogin(@RequestBody JSONObject body, HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();
        String deviceId = headerDeviceId(request);
        if (username.isEmpty() || password.isEmpty()) {
            return new ResultInfo(400, "参数不完整");
        }
        boolean ok = userService.authenticate(username, password);
        if (!ok) return new ResultInfo(400, "用户名或密码错误");
        UserAccount user = userService.findByUsername(username);
        if (user == null) return new ResultInfo(404, "not found");
        session.setAttribute("user", user);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());

        Tokens tokens = tokenService.issueTokens(user, deviceId, remoteIp(request), request.getHeader("User-Agent"));
        setRefreshCookie(response, tokens.getRefreshToken());
        JSONObject data = new JSONObject();
        data.put("accessToken", tokens.getAccessToken());
        data.put("redirect", "./index.html");
        return new ResultInfo(1000, "success", data);
    }

    @PostMapping("/refresh")
    public ResultInfo refresh(HttpServletRequest request, HttpServletResponse response) {
        String deviceId = headerDeviceId(request);
        String rtPlain = readRefreshCookie(request);
        if (rtPlain == null || rtPlain.isEmpty()) {
            return new ResultInfo(401, "unauthorized");
        }
        Optional<RefreshToken> opt = tokenService.findByHash(rtPlain);
        if (!opt.isPresent()) {
            tokenService.audit(null, null, deviceId, remoteIp(request), request.getHeader("User-Agent"), false);
            return new ResultInfo(401, "unauthorized");
        }
        RefreshToken rt = opt.get();
        boolean expired = rt.getExpiresAt() != null && rt.getExpiresAt().before(new Date());
        boolean revoked = Boolean.TRUE.equals(rt.getRevoked());
        if (expired || revoked) {
            tokenService.audit(rt.getId(), rt.getUserId(), deviceId, remoteIp(request), request.getHeader("User-Agent"), false);
            return new ResultInfo(401, "unauthorized");
        }
        Optional<com.krielwus.webtracinganalysis.info.RefreshRotateResult> nextOpt = tokenService.rotate(rt, remoteIp(request), request.getHeader("User-Agent"));
        if (!nextOpt.isPresent()) {
            tokenService.audit(rt.getId(), rt.getUserId(), deviceId, remoteIp(request), request.getHeader("User-Agent"), false);
            return new ResultInfo(401, "unauthorized");
        }
        com.krielwus.webtracinganalysis.info.RefreshRotateResult rotateResult = nextOpt.get();
        RefreshToken next = rotateResult.getNext();
        UserAccount user = userService.findById(rt.getUserId());
        if (user == null) {
            tokenService.audit(rt.getId(), rt.getUserId(), deviceId, remoteIp(request), request.getHeader("User-Agent"), false);
            return new ResultInfo(401, "unauthorized");
        }
        String at = tokenService.issueAccessToken(user, deviceId);
        setRefreshCookie(response, rotateResult.getRefreshPlain());
        tokenService.audit(next.getId(), user.getId(), deviceId, remoteIp(request), request.getHeader("User-Agent"), true);
        JSONObject data = new JSONObject();
        data.put("accessToken", at);
        return new ResultInfo(1000, "success", data);
    }

    @GetMapping("/devices")
    public ResultInfo devices(HttpSession session) {
        Object u = session.getAttribute("user");
        if (!(u instanceof UserAccount)) return new ResultInfo(401, "unauthorized");
        UserAccount user = (UserAccount) u;
        List<Map<String,Object>> list = tokenService.listActiveDevices(user.getId());
        return new ResultInfo(1000, "success", list);
    }

    @PostMapping("/kick")
    public ResultInfo kick(@RequestBody JSONObject body, HttpSession session) {
        Object u = session.getAttribute("user");
        if (!(u instanceof UserAccount)) return new ResultInfo(401, "unauthorized");
        UserAccount user = (UserAccount) u;
        Long tokenId = body.getLong("tokenId");
        if (tokenId == null) return new ResultInfo(400, "tokenId required");
        boolean ok = tokenService.revokeDevice(user.getId(), tokenId);
        if (!ok) return new ResultInfo(400, "bad request");
        return new ResultInfo(1000, "success");
    }

    @PostMapping("/logout")
    public ResultInfo logoutDevice(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        clearRefreshCookie(response);
        try { session.invalidate(); } catch (Exception ignore) {}
        return new ResultInfo(1000, "success");
    }

    @PostMapping("/forceLogout")
    public ResultInfo forceLogout(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isAdminOrSuper(op)) return new ResultInfo(403, "forbidden");
        Long uid = body.getLong("userId");
        if (uid == null) return new ResultInfo(400, "userId required");
        int n = tokenService.forceLogoutUser(uid);
        return new ResultInfo(1000, "success", "已强制下线 " + n + " 个设备");
    }
    
    @GetMapping("/devices/all")
    public ResultInfo allDevices(@RequestParam(value = "q", required = false) String q,
                                 @RequestParam(value = "userId", required = false) Long userId,
                                 HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isAdminOrSuper(op)) return new ResultInfo(403, "forbidden");
        List<Map<String,Object>> list = tokenService.listActiveDevicesAll(q, userId);
        return new ResultInfo(1000, "success", list);
    }
    
    @PostMapping("/kickAny")
    public ResultInfo kickAny(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isAdminOrSuper(op)) return new ResultInfo(403, "forbidden");
        Long tokenId = body.getLong("tokenId");
        if (tokenId == null) return new ResultInfo(400, "tokenId required");
        boolean ok = tokenService.revokeAny(tokenId);
        if (!ok) return new ResultInfo(400, "bad request");
        return new ResultInfo(1000, "success");
    }

    @PostMapping("/sso/callback")
    public ResultInfo ssoCallback(@RequestBody JSONObject body, HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        String username = body.getString("username");
        if (username == null || username.isEmpty()) return new ResultInfo(400, "username required");
        UserAccount user = userService.findByUsername(username);
        if (user == null) return new ResultInfo(404, "not found");
        session.setAttribute("user", user);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());
        String deviceId = headerDeviceId(request);
        Tokens tokens = tokenService.issueTokens(user, deviceId, remoteIp(request), request.getHeader("User-Agent"));
        setRefreshCookie(response, tokens.getRefreshToken());
        JSONObject data = new JSONObject();
        data.put("accessToken", tokens.getAccessToken());
        data.put("redirect", "./index.html");
        return new ResultInfo(1000, "success", data);
    }

    private String headerDeviceId(HttpServletRequest request) {
        String d = request.getHeader("X-Device-Id");
        if (d == null || d.trim().isEmpty()) {
            String ua = request.getHeader("User-Agent");
            d = Integer.toHexString((ua == null ? "unknown" : ua).hashCode());
        }
        return d;
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
            encoded = URLEncoder.encode(rtPlain, StandardCharsets.UTF_8.name());
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

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie c = new Cookie("RT", "");
        c.setHttpOnly(true);
        c.setSecure(false);
        c.setPath("/");
        c.setMaxAge(0);
        response.addCookie(c);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if ("RT".equals(c.getName())) {
                String v = c.getValue();
                if (v == null) return null;
                try {
                    return java.net.URLDecoder.decode(v, StandardCharsets.UTF_8.name());
                } catch (java.io.UnsupportedEncodingException e) {
                    return v;
                }
            }
        }
        return null;
    }

    private boolean isAdminOrSuper(UserAccount ua) {
        return ua != null && ("SUPER_ADMIN".equals(ua.getRole()) || "ADMIN".equals(ua.getRole()));
    }

    // not used
}
