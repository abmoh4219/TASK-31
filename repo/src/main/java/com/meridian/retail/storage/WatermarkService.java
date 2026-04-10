package com.meridian.retail.storage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Watermarking — applies an "INTERNAL USE ONLY | {user} | {date}" diagonal text overlay
 * to PDFs (via PDFBox 3.x) and to images (via plain AWT). The watermark is intentionally
 * visible but not destructive: it does not overwrite the underlying content.
 *
 * Used by MaskedDownloadService when serving an internal-only attachment to a role that
 * is not in the masked_roles list.
 */
@Service
public class WatermarkService {

    /** Adds a diagonal grey watermark to every page of the input PDF and writes the result to {@code out}. */
    public void addPdfWatermark(Path inputPath, String username, OutputStream out) {
        String text = "INTERNAL USE ONLY  |  " + safe(username) + "  |  " + LocalDate.now();
        try (PDDocument doc = Loader.loadPDF(inputPath.toFile())) {

            PDExtendedGraphicsState gfxState = new PDExtendedGraphicsState();
            gfxState.setNonStrokingAlphaConstant(0.30f);
            gfxState.setStrokingAlphaConstant(0.30f);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            for (PDPage page : doc.getPages()) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(gfxState);
                    cs.setNonStrokingColor(Color.GRAY);
                    cs.beginText();
                    cs.setFont(font, 36);
                    // Rotate 45deg around the page center
                    double rad = Math.toRadians(45);
                    float tx = pageWidth / 2f - 200f;
                    float ty = pageHeight / 2f - 50f;
                    cs.setTextMatrix(
                            new org.apache.pdfbox.util.Matrix(
                                    (float) Math.cos(rad), (float) Math.sin(rad),
                                    (float) -Math.sin(rad), (float) Math.cos(rad),
                                    tx, ty
                            )
                    );
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
        } catch (IOException e) {
            throw new StorageException("Failed to watermark PDF: " + e.getMessage(), e);
        }
    }

    /** Adds a diagonal text watermark to a raster image and writes a PNG to {@code out}. */
    public void addImageWatermark(Path inputPath, String mimeType, String username, OutputStream out) {
        String text = "INTERNAL USE ONLY  |  " + safe(username) + "  |  " + LocalDate.now();
        try (InputStream in = Files.newInputStream(inputPath)) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                throw new StorageException("Cannot decode image: " + inputPath);
            }
            BufferedImage out2 = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out2.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g.setColor(Color.LIGHT_GRAY);
            int fontSize = Math.max(18, src.getWidth() / 20);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            g.rotate(Math.toRadians(-30), src.getWidth() / 2.0, src.getHeight() / 2.0);
            int textWidth = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (src.getWidth() - textWidth) / 2, src.getHeight() / 2);
            g.dispose();

            ImageIO.write(out2, "png", out);
        } catch (IOException e) {
            throw new StorageException("Failed to watermark image: " + e.getMessage(), e);
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
