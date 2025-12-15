package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.util.MenuUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 菜单数据接口。
 * 为前端提供菜单配置与菜单列表，支持后续扩展鉴权逻辑。
 */
@Controller
@RequestMapping("/api")
public class MenuController {

    /**
     * 获取菜单配置：返回 Logo、主题、Tab 等配置，以及菜单数据源改造为服务端返回。
     */
    @RequestMapping("/getMenuConfig")
    @ResponseBody
    public JSONObject getMenuConfig(HttpServletRequest request, HttpSession session) {
        ResultInfo resultInfo = new ResultInfo();
        String username = (String) session.getAttribute("username");
        //可做鉴权
//        if (username == null || !username.equals("admin")){
//            return new ResultInfo(403, "用户无权限", null, "");
//        }
        // 获取菜单配置
        JSONObject menuConfig = MenuUtil.getMenuConfig();
        JSONObject menu = JSONObject.parseObject(menuConfig.get("menu").toString());
        menu.remove("data");
        menu.put("data", MenuUtil.getMenu());

        menuConfig.remove("menu");
        menuConfig.put("menu", menu);
        return menuConfig;
    }

    /**
     * 获取菜单列表：直接返回内置菜单结构（演示数据）。
     */
    @RequestMapping("/getMenuList")
    @ResponseBody
    public ResultInfo getMenuList(HttpServletRequest request, HttpSession session) {
        ResultInfo resultInfo = new ResultInfo();
        String username = (String) session.getAttribute("username");
        //可做鉴权
//        if (username == null || !username.equals("admin")){
//            return new ResultInfo(403, "用户无权限", null, "");
//        }
        // 获取菜单
        return new ResultInfo(200, "success", MenuUtil.getMenu(), "");
    }
}
