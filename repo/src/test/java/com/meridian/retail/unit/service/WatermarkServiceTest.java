package com.meridian.retail.unit.service;

import com.meridian.retail.service.*;
import com.meridian.retail.security.*;

import com.meridian.retail.storage.WatermarkService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WatermarkServiceTest {

    private final WatermarkService svc = new WatermarkService();

    @Test
    void watermarkPdfProducesValidPdf() throws Exception {
        Path src = Files.createTempFile("wm-test", ".pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(src.toFile());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        svc.addPdfWatermark(src, "alice", out);
        assertThat(out.size()).isGreaterThan(0);

        // Round-trip parse to confirm the output is still a valid PDF
        try (PDDocument parsed = Loader.loadPDF(out.toByteArray())) {
            assertThat(parsed.getNumberOfPages()).isEqualTo(1);
        }
        Files.deleteIfExists(src);
    }

    @Test
    void watermarkImageProducesValidPng() throws Exception {
        Path src = Files.createTempFile("wm-test", ".png");
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ImageIO.write(img, "png", src.toFile());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        svc.addImageWatermark(src, "image/png", "alice", out);

        assertThat(out.size()).isGreaterThan(0);
        BufferedImage parsed = ImageIO.read(new java.io.ByteArrayInputStream(out.toByteArray()));
        assertThat(parsed).isNotNull();
        assertThat(parsed.getWidth()).isEqualTo(200);

        Files.deleteIfExists(src);
    }
}
