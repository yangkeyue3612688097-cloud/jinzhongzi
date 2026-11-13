package org.example.backend.util;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class HashUtil {

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error", e);
        }
    }

    public static String sha256File(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
            fis.close();
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("File hash error", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
