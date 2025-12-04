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

/**
 * 验证码服务接口。
 * 提供验证码图片生成与验证，用于登录等业务的人机校验。
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private Producer captchaProducer;

    /**
     * 生成并返回验证码图片，同时将验证码文本写入会话。
     * @param response HTTP 响应
     * @param session 会话，用于存储验证码文本
     */
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

    /**
     * 校验会话中的验证码与用户输入是否一致（忽略大小写）。
     * @param inputCaptcha 用户输入验证码
     * @param session 会话
     * @return 校验结果
     */
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
