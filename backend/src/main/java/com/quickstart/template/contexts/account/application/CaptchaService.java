package com.quickstart.template.contexts.account.application;

import com.quickstart.template.contexts.account.api.dto.CaptchaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {
    private static final String CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final Map<String, CaptchaEntry> entries = new ConcurrentHashMap<>();
    private final long captchaTtlSeconds;

    public CaptchaService(@Value("${app.auth.captcha-ttl-seconds:300}") long captchaTtlSeconds) {
        this.captchaTtlSeconds = captchaTtlSeconds;
    }

    public CaptchaResponse createCaptcha() {
        cleanupExpired();

        String answer = generateAnswer();
        String captchaId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(captchaTtlSeconds);
        entries.put(captchaId, new CaptchaEntry(answer, expiresAt));

        return new CaptchaResponse(captchaId, renderCaptcha(answer), captchaTtlSeconds);
    }

    public boolean verifyCaptcha(String captchaId, String answer) {
        cleanupExpired();

        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }

        CaptchaEntry entry = entries.get(captchaId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(captchaId);
            return false;
        }

        boolean matched = entry.answer().equalsIgnoreCase(answer.trim());
        if (matched) {
            entries.remove(captchaId);
        }
        return matched;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String generateAnswer() {
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            int index = ThreadLocalRandom.current().nextInt(CHARSET.length());
            builder.append(CHARSET.charAt(index));
        }
        return builder.toString();
    }

    private String renderCaptcha(String answer) {
        BufferedImage image = new BufferedImage(140, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        graphics.setColor(new Color(247, 249, 252));
        graphics.fillRect(0, 0, 140, 48);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 28));

        for (int i = 0; i < 10; i++) {
            graphics.setColor(new Color(220, 228, 237));
            int x1 = ThreadLocalRandom.current().nextInt(140);
            int y1 = ThreadLocalRandom.current().nextInt(48);
            int x2 = ThreadLocalRandom.current().nextInt(140);
            int y2 = ThreadLocalRandom.current().nextInt(48);
            graphics.drawLine(x1, y1, x2, y2);
        }

        for (int i = 0; i < answer.length(); i++) {
            graphics.setColor(new Color(60 + i * 20, 80, 120 + i * 10));
            int yOffset = ThreadLocalRandom.current().nextInt(-4, 5);
            graphics.drawString(String.valueOf(answer.charAt(i)), 18 + i * 28, 32 + yOffset);
        }

        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate captcha image", exception);
        }
    }

    private record CaptchaEntry(String answer, Instant expiresAt) {}
}
