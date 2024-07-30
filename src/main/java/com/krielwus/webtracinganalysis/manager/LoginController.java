package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Controller
public class LoginController {

    @RequestMapping("/login")
    @ResponseBody
    public ResultInfo login(HttpServletRequest request, HttpServletResponse response, HttpSession session) throws IOException {
        ResultInfo resultInfo = new ResultInfo();

        // 获取json数据
        ServletInputStream inputStream = request.getInputStream();
        StringBuilder sb = new StringBuilder();
        String line = null;

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        //入参转json对象
        JSONObject jsonObject = JSONObject.parseObject(String.valueOf(sb));
        String username = String.valueOf(jsonObject.get("username"));
        String password = String.valueOf(jsonObject.get("password"));
        String verifyCode = String.valueOf(jsonObject.get("verifyCode"));

        String captcha = (String) session.getAttribute("captcha");
        if (captcha == null || ""==captcha){
            return new ResultInfo(400, "验证码已过期");
        }
        if (captcha != null && captcha.equalsIgnoreCase(verifyCode)) {
//            return new ResultInfo(200, "验证码正确");
            if (username.equals("admin") && password.equals("admin")) {
                session.setAttribute("username", username);
                return new ResultInfo(200, "登录成功", "","./index.html");
            }
        } else {
            return new ResultInfo(400, "验证码错误");
        }
        return resultInfo;
    }


}
