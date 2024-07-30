package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.util.MenuUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Controller
public class MenuController {

    @RequestMapping("/getMenuConfig")
    @ResponseBody
    public JSONObject getMenuConfig(HttpServletRequest request, HttpSession session) {
        ResultInfo resultInfo = new ResultInfo();
        String username = (String) session.getAttribute("username");
        //可做鉴权
//        if (username == null || !username.equals("admin")){
//            return new ResultInfo(403, "用户无权限", null, "");
//        }
        //获取菜单配置
        JSONObject menuConfig = MenuUtil.getMenuConfig();
        JSONObject menu = JSONObject.parseObject(menuConfig.get("menu").toString());
        menu.remove("data");
        menu.put("data", MenuUtil.getMenu());

        menuConfig.remove("menu");
        menuConfig.put("menu", menu);
        return menuConfig;
    }

    @RequestMapping("/getMenuList")
    @ResponseBody
    public ResultInfo getMenuList(HttpServletRequest request, HttpSession session) {
        ResultInfo resultInfo = new ResultInfo();
        String username = (String) session.getAttribute("username");
        //可做鉴权
//        if (username == null || !username.equals("admin")){
//            return new ResultInfo(403, "用户无权限", null, "");
//        }
        //获取菜单
        return new ResultInfo(200, "success", MenuUtil.getMenu(), "");
    }
}
