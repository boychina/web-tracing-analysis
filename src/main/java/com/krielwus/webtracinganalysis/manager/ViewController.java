package com.krielwus.webtracinganalysis.manager;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/view")
public class ViewController {

    //进入首页
    @RequestMapping("/console/console1.html")
    public String toIndex() {
        return "/view/console/index";
    }

    //view/console/index.html
    @RequestMapping("/console/index.html")
    public String toConsole() {
        return "/view/console/index";
    }



    //分析页
    @RequestMapping("/analysis/index.html")
    public String toAnalysis() {
        return "/view/analysis/index";
    }
    //行为页
    @RequestMapping("/analysis/userTrack.html")
    public String toBehavior() {
        return "/view/analysis/userTrack";
    }


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
