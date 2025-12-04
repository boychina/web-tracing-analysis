package com.krielwus.webtracinganalysis.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 菜单工具。
 * 提供演示环境下的菜单与配置数据源，前端可直接使用服务端返回的数据渲染导航与 Tab。
 */
public class MenuUtil {

    /** 菜单列表（演示数据，前端直接渲染） */
    private static String menu = "[\n" +
            "\t{\n" +
            "\t\t\"id\": 1,\n" +
            "\t\t\"title\": \"工作空间\",\n" +
            "\t\t\"icon\": \"layui-icon layui-icon-console\",\n" +
            "\t\t\"type\": 0,\n" +
            "\t\t\"children\": [\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\": \"10\",\n" +
            "\t\t\t\t\"title\": \"分析页\",\n" +
            "\t\t\t\t\"icon\": \"layui-icon layui-icon-console\",\n" +
            "\t\t\t\t\"type\": 1,\n" +
            "\t\t\t\t\"openType\": \"_component\",\n" +
            "\t\t\t\t\"href\": \"../view/analysis/index.html\"\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\": \"11\",\n" +
            "\t\t\t\t\"title\": \"工作台\",\n" +
            "\t\t\t\t\"icon\": \"layui-icon layui-icon-console\",\n" +
            "\t\t\t\t\"type\": 1,\n" +
            "\t\t\t\t\"openType\": \"_component\",\n" +
            "\t\t\t\t\"href\": \"view/console/index.html\"\n" +
            "\t\t\t},\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"id\": \"12\",\n" +
            "\t\t\t\t\"title\": \"应用监控\",\n" +
            "\t\t\t\t\"icon\": \"layui-icon layui-icon-console\",\n" +
            "\t\t\t\t\"type\": 1,\n" +
            "\t\t\t\t\"openType\": \"_component\",\n" +
            "\t\t\t\t\"href\": \"../view/application/monitor.html\"\n" +
            "\t\t\t}\n" +
            "\t\t]\n" +
            "\t}\n" +
            "]";


    /** 菜单配置（Logo、主题、Tab 配置等） */
    private static String menuConfigString = "{\n" +
            "\t\"logo\": {\n" +
            "\t\t\"title\": \"Pear Admin\",\n" +
            "\t\t\"image\": \"admin/images/logo.png\"\n" +
            "\t},\n" +
            "\t\"menu\": {\n" +
            "\t\t\"data\": \"admin/data/menu.json\",\n" +
            "\t\t\"method\": \"GET\",\n" +
            "\t\t\"accordion\": true,\n" +
            "\t\t\"collapse\": false,\n" +
            "\t\t\"control\": false,\n" +
            "\t\t\"controlWidth\": 500,\n" +
            "\t\t\"select\": \"10\",\n" +
            "\t\t\"async\": true\n" +
            "\t},\n" +
            "\t\"tab\": {\n" +
            "\t\t\"enable\": true,\n" +
            "\t\t\"keepState\": true,\n" +
            "\t\t\"session\": true,\n" +
            "\t\t\"preload\": false,\n" +
            "\t\t\"max\": \"30\",\n" +
            "\t\t\"index\": {\n" +
            "\t\t\t\"id\": \"10\",\n" +
            "\t\t\t\"href\": \"../view/analysis/index.html\",\n" +
            "\t\t\t\"title\": \"首页\"\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t\"theme\": {\n" +
            "\t\t\"defaultColor\": \"2\",\n" +
            "\t\t\"defaultMenu\": \"dark-theme\",\n" +
            "\t\t\"defaultHeader\": \"light-theme\",\n" +
            "\t\t\"allowCustom\": true,\n" +
            "\t\t\"banner\": false\n" +
            "\t},\n" +
            "\t\"colors\": [\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"1\",\n" +
            "\t\t\t\"color\": \"#2d8cf0\",\n" +
            "\t\t\t\"second\": \"#ecf5ff\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"2\",\n" +
            "\t\t\t\"color\": \"#36b368\",\n" +
            "\t\t\t\"second\": \"#f0f9eb\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"3\",\n" +
            "\t\t\t\"color\": \"#f6ad55\",\n" +
            "\t\t\t\"second\": \"#fdf6ec\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"4\",\n" +
            "\t\t\t\"color\": \"#f56c6c\",\n" +
            "\t\t\t\"second\": \"#fef0f0\"\n" +
            "\t\t},\n" +
            "\t\t{\n" +
            "\t\t\t\"id\": \"5\",\n" +
            "\t\t\t\"color\": \"#3963bc\",\n" +
            "\t\t\t\"second\": \"#ecf5ff\"\n" +
            "\t\t}\n" +
            "\t],\n" +
            "\t\"other\": {\n" +
            "\t\t\"keepLoad\": \"1200\",\n" +
            "\t\t\"autoHead\": false,\n" +
            "\t\t\"footer\": false\n" +
            "\t},\n" +
            "\t\"header\": {\n" +
            "\t\t\"message\": \"admin/data/message.json\"\n" +
            "\t}\n" +
            "}";







    /** 获取菜单配置 JSON */
    public static JSONObject getMenuConfig() {
        JSONObject menuConfigJson = JSONObject.parseObject(String.valueOf(menuConfigString.toString()));
//        JSONArray menuJson = JSONArray.parseArray(menu);
        return menuConfigJson;
    }


    /** 获取菜单列表 JSON 数组 */
    public static JSONArray getMenu() {
//        JSONObject menuJson = JSONObject.parseObject(String.valueOf(menu.toString()));
        JSONArray menuJson = JSONArray.parseArray(menu);
        return menuJson;
    }

    /** 控制台打印示例 */
    public static void main(String[] args) {
        System.out.println(getMenu());
        System.out.println(getMenuConfig());
    }
}
