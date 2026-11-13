package org.example.backend.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.example.backend.dto.PublicEvidenceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the one-page provenance certificate as a PDF without a web view.
 * The page includes the generation parameters and a QR code pointing at the
 * public verification page, so a judge can scan it back to /verify/:hash.
 */
public final class CertificatePdfBuilder {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfBuilder.class);

    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final int MARGIN = 72;

    private static final Color INK = new Color(38, 42, 48);
    private static final Color MUTED = new Color(105, 113, 128);
    private static final Color ACCENT = new Color(36, 87, 147);
    private static final Color PANEL = new Color(245, 247, 250);
    private static final Color LINE = new Color(222, 226, 232);
    private static final Color OK = new Color(21, 128, 61);
    private static final Color WARN = new Color(180, 83, 9);

    private CertificatePdfBuilder() {
    }

    public static byte[] build(PublicEvidenceVO vo, String verifyUrl) {
        Font base = loadFont();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(18, 18, WIDTH - 36, HEIGHT - 36);
            g.setColor(ACCENT);
            g.fillRect(18, 18, 16, HEIGHT - 36);

            drawHeader(g, base, vo);
            drawLeftColumn(g, base, vo);
            drawQrPanel(g, base, verifyUrl);
            drawCanonicalBox(g, base, vo);
            drawFooter(g, base, vo, verifyUrl);

            return PdfImageWriter.onePageImage(image);
        } finally {
            g.dispose();
        }
    }

    private static void drawHeader(Graphics2D g, Font base, PublicEvidenceVO vo) {
        g.setColor(ACCENT);
        g.fillRect(MARGIN, 56, WIDTH - MARGIN * 2, 112);

        g.setColor(Color.WHITE);
        Font title = base.deriveFont(Font.BOLD, 34f);
        g.setFont(title);
        g.drawString("AI 创作来源存证证书", MARGIN + 28, 116);

        Font subtitle = base.deriveFont(Font.PLAIN, 16f);
        g.setFont(subtitle);
        g.drawString("AI-Generated Content Provenance Certificate", MARGIN + 29, 146);

        Font idFont = base.deriveFont(Font.PLAIN, 13f);
        g.setFont(idFont);
        String shortHash = vo.getDataHash() == null ? "-" : vo.getDataHash();
        if (shortHash.length() > 18) {
            shortHash = shortHash.substring(0, 18);
        }
        int rightX = MARGIN + (WIDTH - MARGIN * 2) - 30;
        FontMetrics fm = g.getFontMetrics();
        g.drawString("dataHash " + shortHash, rightX - fm.stringWidth("dataHash " + shortHash), 118);
    }

    private static void drawLeftColumn(Graphics2D g, Font base, PublicEvidenceVO vo) {
        int x = MARGIN;
        int y = 224;
        int width = 590;

        Font section = base.deriveFont(Font.BOLD, 20f);
        Font label = base.deriveFont(Font.PLAIN, 14f);
        Font value = base.deriveFont(Font.PLAIN, 19f);

        g.setColor(INK);
        g.setFont(section);
        g.drawString("作品与文件", x, y);
        y += 46;
        y = drawField(g, label, value, "作品名称", vo.getTitle(), x, y, width);
        y = drawField(g, label, value, "创作者", vo.getAuthorName(), x, y, width);
        y = drawField(g, label, value, "文件类型", vo.getFileType(), x, y, width);
        y = drawField(g, label, value, "存证时间", text(vo.getCreateTime()), x, y, width);
        y = drawField(g, label, value, "数据哈希", vo.getDataHash(), x, y, width);
        y = drawField(g, label, value, "文件指纹 SHA-256", vo.getFileHash(), x, y, width);

        g.setFont(section);
        g.setColor(INK);
        y += 24;
        g.drawString("生成参数", x, y);
        y += 46;
        y = drawField(g, label, value, "模型", modelLabel(vo), x, y, width);
        y = drawField(g, label, value, "参数", generationSummary(vo), x, y, width);
        y = drawField(g, label, value, "Prompt", generationValue(vo, "prompt"), x, y, width);
        y = drawField(g, label, value, "Negative Prompt", generationValue(vo, "negativePrompt"), x, y, width);
        y = drawField(g, label, value, "Sampler / Scheduler",
                generationValue(vo, "sampler") + " / " + generationValue(vo, "scheduler"), x, y, width);
        y = drawField(g, label, value, "Workflow Hash", generationValue(vo, "workflowHash"), x, y, width);

        g.setFont(section);
        y += 24;
        g.drawString("身份与链上状态", x, y);
        y += 46;
        y = drawField(g, label, value, "创作者地址", vo.getCreatorAddress(), x, y, width);
        y = drawField(g, label, value, "链上状态", vo.getChainMessage(), x, y, width);
    }

    private static int drawField(Graphics2D g, Font labelFont, Font valueFont, String label,
                                 String value, int x, int y, int width) {
        g.setFont(labelFont);
        g.setColor(MUTED);
        g.drawString(label, x, y + 18);

        g.setFont(valueFont);
        g.setColor(INK);
        List<String> lines = wrap(value, g.getFontMetrics(valueFont), width);
        int lineHeight = 34;
        int cursor = y + 24 + lineHeight;
        for (String line : lines) {
            g.drawString(line, x, cursor);
            cursor += lineHeight;
        }
        return cursor + 8;
    }

    private static void drawQrPanel(Graphics2D g, Font base, String verifyUrl) {
        int x = 770;
        int y = 224;
        int size = 300;

        g.setColor(PANEL);
        g.fillRoundRect(x - 16, y - 16, size + 32, size + 144, 14, 14);
        g.setColor(LINE);
        g.drawRoundRect(x - 16, y - 16, size + 32, size + 144, 14, 14);

        drawQrCode(g, verifyUrl, x, y, size);

        g.setColor(MUTED);
        Font font = base.deriveFont(Font.PLAIN, 13f);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(verifyUrl, fm, size);
        int cursor = y + size + 44;
        for (String line : lines) {
            g.setColor(MUTED);
            g.drawString(line, x, cursor);
            cursor += 22;
        }
    }

    private static void drawCanonicalBox(Graphics2D g, Font base, PublicEvidenceVO vo) {
        int x = MARGIN;
        int y = HEIGHT - 178;
        int width = WIDTH - MARGIN * 2;

        g.setColor(PANEL);
        g.fillRoundRect(x, y - 20, width, 118, 10, 10);
        g.setColor(LINE);
        g.drawRoundRect(x, y - 20, width, 118, 10, 10);

        g.setColor(INK);
        g.setFont(base.deriveFont(Font.BOLD, 15f));
        g.drawString("规范化内容摘要（完整内容以 manifest.json 为准）", x + 20, y + 8);

        g.setColor(MUTED);
        g.setFont(base.deriveFont(Font.PLAIN, 13f));
        FontMetrics fm = g.getFontMetrics();
        String payload = vo.getCanonicalPayload() == null ? "" : vo.getCanonicalPayload();
        List<String> lines = wrap(payload, fm, width - 40);
        int shown = Math.min(lines.size(), 3);
        for (int i = 0; i < shown; i++) {
            g.drawString(lines.get(i), x + 20, y + 30 + i * 22);
        }
        if (lines.size() > shown) {
            g.drawString("…… 内容较长，请以哈希值与 manifest 文件核验", x + 20, y + 30 + shown * 22);
        }
    }

    private static void drawFooter(Graphics2D g, Font base, PublicEvidenceVO vo, String verifyUrl) {
        g.setColor(LINE);
        g.drawLine(MARGIN, HEIGHT - 52, WIDTH - MARGIN, HEIGHT - 52);
        g.setFont(base.deriveFont(Font.PLAIN, 12f));
        g.setColor(MUTED);
        g.drawString("哈希算法 " + vo.getHashAlgorithm(), MARGIN, HEIGHT - 24);
        String status = "签名 " + (vo.isSignatureVerified() ? "已验证" : "未签名");
        g.setColor(vo.isSignatureVerified() ? OK : WARN);
        g.drawString(status, WIDTH - MARGIN - 120, HEIGHT - 24);
    }

    private static void drawQrCode(Graphics2D g, String text, int x, int y, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            int modules = matrix.getWidth();
            int cell = Math.max(1, size / modules);
            int used = cell * modules;
            int offsetX = x + (size - used) / 2;
            int offsetY = y + (size - used) / 2;
            g.setColor(Color.BLACK);
            for (int row = 0; row < modules; row++) {
                for (int col = 0; col < modules; col++) {
                    if (matrix.get(col, row)) {
                        g.fillRect(offsetX + col * cell, offsetY + row * cell, cell, cell);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to render verification QR code", e);
        }
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("-");
            return lines;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\n", -1);
        for (String paragraph : paragraphs) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                if (fm.stringWidth(line.toString() + paragraph.charAt(i)) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(paragraph.charAt(i));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static Font loadFont() {
        String[] candidates = {
                "/mnt/c/Windows/Fonts/msyh.ttc",
                "C:\\Windows\\Fonts\\msyh.ttc",
                "/mnt/c/Windows/Fonts/simhei.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };
        for (String candidate : candidates) {
            try {
                File file = new File(candidate);
                if (file.exists()) {
                    return Font.createFont(Font.TRUETYPE_FONT, file);
                }
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
        return new Font(Font.SERIF, Font.PLAIN, 14);
    }

    private static String modelLabel(PublicEvidenceVO vo) {
        Map<String, Object> generation = vo.getGeneration();
        if (generation == null) {
            return "-";
        }
        Object model = generation.get("modelName");
        Object version = generation.get("modelVersion");
        return text(model) + (notBlank(version) ? " / " + version : "");
    }

    private static String generationSummary(PublicEvidenceVO vo) {
        Map<String, Object> generation = vo.getGeneration();
        if (generation == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        if (notBlank(generation.get("width")) || notBlank(generation.get("height"))) {
            builder.append(text(generation.get("width"))).append("x").append(text(generation.get("height")));
        }
        append(builder, "steps", generation.get("steps"));
        append(builder, "CFG", generation.get("cfgScale"));
        append(builder, "seed", generation.get("seed"));
        return builder.length() == 0 ? "-" : builder.toString().trim();
    }

    private static String generationValue(PublicEvidenceVO vo, String key) {
        Map<String, Object> generation = vo.getGeneration();
        return generation == null ? "-" : text(generation.get(key));
    }

    private static void append(StringBuilder builder, String label, Object value) {
        if (notBlank(value)) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(label).append(" ").append(value);
        }
    }

    private static boolean notBlank(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private static String text(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
