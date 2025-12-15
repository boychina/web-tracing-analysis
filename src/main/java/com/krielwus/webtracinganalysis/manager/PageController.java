package com.krielwus.webtracinganalysis.manager;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PageController {

    @RequestMapping({
            "/",
            "/login",
            "/{path:^(?!(api|assets)$).*$}",
            "/{path:^(?!(api|assets)$).*$}/**"
    })
    public String index() {
        return "index";
    }
}
