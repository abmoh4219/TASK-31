package com.meridian.retail.controller;

import com.meridian.retail.security.LocalCaptchaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * CAPTCHA endpoints.
 *   GET  /captcha/image    -> PNG of a freshly generated CAPTCHA, plaintext stored in session
 *   POST /captcha/validate -> small HTML fragment for HTMX (used to live-preview validity)
 */
@Controller
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final LocalCaptchaService captchaService;

    @GetMapping(value = "/image", produces = "image/png")
    public void image(HttpServletResponse response, HttpSession session) throws IOException {
        BufferedImage img = captchaService.generateImage(session);
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        ImageIO.write(img, "png", response.getOutputStream());
    }

    @PostMapping(value = "/validate", produces = "text/html")
    @ResponseBody
    public String validate(@RequestParam("captcha") String input, HttpSession session) {
        boolean ok = captchaService.validateAnswer(session, input);
        if (ok) {
            return "<span class='field-validation-success'><i class='bi bi-check-circle'></i> CAPTCHA accepted</span>";
        }
        return "<span class='field-validation-error'><i class='bi bi-x-circle'></i> Incorrect — try again</span>";
    }
}
