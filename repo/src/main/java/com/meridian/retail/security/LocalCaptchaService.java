package com.meridian.retail.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/**
 * Locally-generated CAPTCHA — no external service, no internet required.
 *
 * generateImage() draws a 200x60 BufferedImage of 6 random alphanumeric characters
 * with mild rotation and a few noise lines. The plaintext answer is stashed in the
 * HTTP session under {@link #SESSION_KEY}; validateAnswer() reads it back, compares
 * case-insensitively, and clears the session attribute on success so the same answer
 * cannot be replayed.
 */
@Service
public class LocalCaptchaService {

    public static final String SESSION_KEY = "CAPTCHA_ANSWER";

    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // skip ambiguous chars
    private static final int CODE_LENGTH = 6;
    private static final int WIDTH  = 200;
    private static final int HEIGHT = 60;
    private static final SecureRandom RNG = new SecureRandom();

    /** Generates a CAPTCHA image and stores its plaintext answer in the session. */
    public BufferedImage generateImage(HttpSession session) {
        String code = randomCode();
        session.setAttribute(SESSION_KEY, code);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Noise lines
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(RNG.nextInt(120) + 80, RNG.nextInt(120) + 80, RNG.nextInt(120) + 80));
            g.drawLine(RNG.nextInt(WIDTH), RNG.nextInt(HEIGHT),
                       RNG.nextInt(WIDTH), RNG.nextInt(HEIGHT));
        }

        // Characters with per-char rotation
        Font baseFont = new Font(Font.SANS_SERIF, Font.BOLD, 32);
        int x = 18;
        for (char c : code.toCharArray()) {
            int fontSize = 30 + RNG.nextInt(7); // 30..36
            Font font = baseFont.deriveFont((float) fontSize);
            g.setFont(font);

            double angleDegrees = (RNG.nextDouble() * 30) - 15; // -15..+15 degrees
            AffineTransform original = g.getTransform();
            AffineTransform rotated = new AffineTransform(original);
            rotated.rotate(Math.toRadians(angleDegrees), x + 10, HEIGHT / 2.0);
            g.setTransform(rotated);

            g.setColor(new Color(30 + RNG.nextInt(60), 30 + RNG.nextInt(60), 80 + RNG.nextInt(80)));
            g.drawString(String.valueOf(c), x, HEIGHT - 18);

            g.setTransform(original);
            x += 28;
        }

        g.dispose();
        return img;
    }

    /**
     * Compares the user's typed answer to the value stashed in the session, case-insensitively.
     * On success the answer is removed from the session so the same image cannot be reused.
     */
    public boolean validateAnswer(HttpSession session, String userInput) {
        if (session == null || userInput == null) return false;
        Object stored = session.getAttribute(SESSION_KEY);
        if (stored == null) return false;
        boolean match = stored.toString().equalsIgnoreCase(userInput.trim());
        if (match) {
            session.removeAttribute(SESSION_KEY);
        }
        return match;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(RNG.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
