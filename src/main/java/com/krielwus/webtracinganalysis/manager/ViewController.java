package com.krielwus.webtracinganalysis.manager;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 视图模板路由。
 * 负责将特定路径映射到 Thymeleaf 模板，用于展示控制台、分析页与异常页等。
 */
@Controller
@RequestMapping("/view")
public class ViewController {

    /** 控制台首页（示例入口） */
    @RequestMapping("/console/console1.html")
    public String toIndex() {
        return "/view/console/index";
    }

    /** 控制台首页 */
    @RequestMapping("/console/index.html")
    public String toConsole() {
        return "/view/console/index";
    }



    /** 监控分析页 */
    @RequestMapping("/analysis/index.html")
    public String toAnalysis() {
        return "/view/analysis/index";
    }
    /** 行为追踪页 */
    @RequestMapping("/analysis/userTrack.html")
    public String toBehavior() {
        return "/view/analysis/userTrack";
    }

    /** 列表页（table 组件示例） */
    @RequestMapping("/listing/table.html")
    public String toListingTable() {
        return "/view/listing/table";
    }

    @RequestMapping("/application/index.html")
    public String toApplication() { return "/view/application/index"; }

    @RequestMapping("/application/monitor.html")
    public String toApplicationMonitor() { return "/view/application/monitor"; }

    @RequestMapping("/user/index.html")
    public String toUserIndex() { return "/view/user/index"; }

    @RequestMapping("/403")
    public String exception_403() {
        return "view/exception/403";
    }
    @RequestMapping("/404")
    public String exception_404() {
        return "view/exception/404";
    }
    @RequestMapping("/500")
    public String exception_500() {
        return "view/exception/500";
    }
}
