package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import com.krielwus.webtracinganalysis.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final UserAccountRepository repo;

    public UserController(UserService userService, UserAccountRepository repo) {
        this.userService = userService;
        this.repo = repo;
    }

    @GetMapping("/me")
    public ResultInfo me(HttpSession session) {
        Object u = session.getAttribute("user");
        if (u instanceof UserAccount) {
            UserAccount ua = (UserAccount) u;
            Map<String,Object> m = new HashMap<>();
            m.put("id", ua.getId());
            m.put("username", ua.getUsername());
            m.put("role", ua.getRole());
            return new ResultInfo(1000, "success", m);
        }
        String name = String.valueOf(session.getAttribute("username"));
        if (name == null || name.isEmpty()) return new ResultInfo(401, "unauthorized");
        UserAccount ua = userService.findByUsername(name);
        if (ua == null) return new ResultInfo(404, "not found");
        Map<String,Object> m = new HashMap<>();
        m.put("id", ua.getId());
        m.put("username", ua.getUsername());
        m.put("role", ua.getRole());
        return new ResultInfo(1000, "success", m);
    }

    @GetMapping("/list")
    public ResultInfo list() {
        List<UserAccount> all = repo.findAll();
        List<Map<String,Object>> out = new ArrayList<>();
        for (UserAccount ua : all) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", ua.getId());
            m.put("username", ua.getUsername());
            m.put("role", ua.getRole());
            m.put("createdAt", ua.getCreatedAt());
            out.add(m);
        }
        return new ResultInfo(1000, "success", out);
    }

    @PostMapping("/create")
    public ResultInfo create(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isAdminOrSuper(op)) return new ResultInfo(403, "forbidden");
        String username = body.getString("username");
        String password = body.getString("password");
        String role = body.getString("role");
        if (!isSuper(op)) role = "USER";
        UserAccount created = userService.create(username, password, role);
        if (created == null) return new ResultInfo(400, "bad request");
        return new ResultInfo(1000, "success", toMap(created));
    }

    @PostMapping("/update")
    public ResultInfo update(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        Long id = body.getLong("id");
        String username = body.getString("username");
        String password = body.getString("password");
        String role = body.getString("role");
        if (!isAdminOrSuper(op)) {
            if (id == null) return new ResultInfo(400, "id required");
            UserAccount targetSelf = repo.findById(id).orElse(null);
            if (targetSelf == null) return new ResultInfo(404, "not found");
            if (!operator.equalsIgnoreCase(targetSelf.getUsername())) return new ResultInfo(403, "forbidden");
            UserAccount saved = userService.update(id, username, password, null);
            return new ResultInfo(1000, "success", toMap(saved));
        }
        if (!isSuper(op)) role = null;
        UserAccount target = repo.findById(id).orElse(null);
        if (target == null) return new ResultInfo(404, "not found");
        if (isAdminName(target.getUsername()) && !isSuper(op)) return new ResultInfo(403, "forbidden");
        if (isAdminName(target.getUsername()) && role != null) return new ResultInfo(403, "forbidden");
        UserAccount saved = userService.update(id, username, password, role);
        return new ResultInfo(1000, "success", toMap(saved));
    }

    @PostMapping("/delete")
    public ResultInfo delete(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isAdminOrSuper(op)) return new ResultInfo(403, "forbidden");
        Long id = body.getLong("id");
        UserAccount target = repo.findById(id).orElse(null);
        if (target == null) return new ResultInfo(404, "not found");
        if ("ADMIN".equals(op.getRole()) && operator != null && operator.equalsIgnoreCase(target.getUsername())) return new ResultInfo(403, "forbidden");
        if (isAdminName(target.getUsername())) return new ResultInfo(403, "forbidden");
        boolean ok = userService.delete(id);
        if (!ok) return new ResultInfo(400, "bad request");
        return new ResultInfo(1000, "success");
    }

    @PostMapping("/setRole")
    public ResultInfo setRole(@RequestBody JSONObject body, HttpSession session) {
        String operator = String.valueOf(session.getAttribute("username"));
        UserAccount op = userService.findByUsername(operator);
        if (op == null) return new ResultInfo(401, "unauthorized");
        if (!isSuper(op)) return new ResultInfo(403, "forbidden");
        Long id = body.getLong("id");
        String role = body.getString("role");
        if (role == null) return new ResultInfo(400, "role required");
        if (!"ADMIN".equals(role) && !"USER".equals(role)) return new ResultInfo(400, "role invalid");
        UserAccount target = repo.findById(id).orElse(null);
        if (target == null) return new ResultInfo(404, "not found");
        if (isAdminName(target.getUsername())) return new ResultInfo(403, "forbidden");
        UserAccount saved = userService.update(id, null, null, role);
        return new ResultInfo(1000, "success", toMap(saved));
    }

    private boolean isSuper(UserAccount ua) { return "SUPER_ADMIN".equals(ua.getRole()); }
    private boolean isAdminOrSuper(UserAccount ua) { return isSuper(ua) || "ADMIN".equals(ua.getRole()); }
    private boolean isAdminName(String name) { return name != null && name.equalsIgnoreCase("admin"); }
    private Map<String,Object> toMap(UserAccount ua) {
        Map<String,Object> m = new HashMap<>();
        m.put("id", ua.getId());
        m.put("username", ua.getUsername());
        m.put("role", ua.getRole());
        m.put("createdAt", ua.getCreatedAt());
        return m;
    }
}
