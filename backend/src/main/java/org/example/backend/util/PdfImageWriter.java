package org.example.backend.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Minimal single-page PDF writer that embeds one full-page raster image.
 * Keeps the certificate generation dependency-free apart from the JDK.
 */
public final class PdfImageWriter {

    private PdfImageWriter() {
    }

    public static byte[] onePageImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgb = toRgb(image, width, height);
        byte[] compressed = deflate(rgb);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);

        offsets.add(startObject(out, 1));
        write(out, "<< /Type /Catalog /Pages 2 0 R >>");
        endObject(out);

        offsets.add(startObject(out, 2));
        write(out, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        endObject(out);

        offsets.add(startObject(out, 3));
        write(out, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height + "] "
                + "/Resources << /XObject << /Im0 4 0 R >> /ProcSet [/PDF /ImageB /ImageC /ImageI] >> "
                + "/Contents 5 0 R >>");
        endObject(out);

        offsets.add(startObject(out, 4));
        write(out, "<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + compressed.length + " >>\nstream\n");
        write(out, compressed);
        write(out, "\nendstream");
        endObject(out);

        offsets.add(startObject(out, 5));
        String content = "q " + width + " 0 0 " + height + " 0 0 cm /Im0 Do Q";
        write(out, "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        endObject(out);

        int xrefOffset = out.size();
        write(out, "xref\n0 " + offsets.size() + "\n");
        write(out, "0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            write(out, formatXref(offsets.get(i)) + " 00000 n \n");
        }
        write(out, "trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\n");
        write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static int startObject(ByteArrayOutputStream out, int number) {
        int offset = out.size();
        write(out, number + " 0 obj\n");
        return offset;
    }

    private static void endObject(ByteArrayOutputStream out) {
        write(out, "\nendobj\n");
    }

    private static String formatXref(int offset) {
        String value = String.valueOf(offset);
        StringBuilder builder = new StringBuilder();
        for (int i = value.length(); i < 10; i++) {
            builder.append('0');
        }
        return builder.append(value).toString();
    }

    private static byte[] toRgb(BufferedImage image, int width, int height) {
        byte[] rgb = new byte[width * height * 3];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                rgb[index++] = (byte) ((pixel >> 16) & 0xff);
                rgb[index++] = (byte) ((pixel >> 8) & 0xff);
                rgb[index++] = (byte) (pixel & 0xff);
            }
        }
        return rgb;
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
