package com.krielwus.webtracinganalysis.manager;

import com.google.code.kaptcha.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private Producer captchaProducer;

    @GetMapping("/getCode")
    public void getCaptcha(HttpServletResponse response, HttpSession session) throws IOException {
        response.setContentType("image/jpeg");
        String capText = captchaProducer.createText();
        session.setAttribute("captcha", capText);
        BufferedImage bi = captchaProducer.createImage(capText);
        ServletOutputStream out = response.getOutputStream();
        ImageIO.write(bi, "jpg", out);
        try {
            out.flush();
        } finally {
            out.close();
        }
    }

    //校验验证码
    @GetMapping("/checkCode")
    public boolean validateCaptcha(String inputCaptcha, HttpSession session) {
        String captcha = (String) session.getAttribute("captcha");
        if (captcha != null && captcha.equalsIgnoreCase(inputCaptcha)) {
            return true;
        } else {
            return false;
        }
    }


}

