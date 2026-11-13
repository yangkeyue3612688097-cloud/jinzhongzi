package org.example.backend.chain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Converts business identifiers and SHA-256 digests into the fixed-width
 * values expected by BusinessAnchor.
 */
public final class ChainEncoding {

    private static final Pattern SHA_256 =
            Pattern.compile("^(?:0x)?[0-9a-fA-F]{64}$");

    private ChainEncoding() {
    }

    public static byte[] sha256ToBytes32(String dataHash) {
        String normalized = normalizeSha256(dataHash);
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            int offset = index * 2;
            result[index] = (byte) Integer.parseInt(
                    normalized.substring(offset, offset + 2), 16);
        }
        return result;
    }

    public static String sha256ToHex(String dataHash) {
        return "0x" + normalizeSha256(dataHash);
    }

    /**
     * Right-aligns a positive long in bytes32, matching Solidity's
     * bytes32(uint256(businessId)).
     */
    public static byte[] businessIdToBytes32(Long businessId) {
        if (businessId == null || businessId < 0) {
            throw new IllegalArgumentException("Business id must be a non-negative long");
        }

        byte[] source = BigInteger.valueOf(businessId).toByteArray();
        int sourceOffset = source.length > 1 && source[0] == 0 ? 1 : 0;
        int sourceLength = source.length - sourceOffset;
        if (sourceLength > 32) {
            throw new IllegalArgumentException("Business id does not fit in bytes32");
        }

        byte[] result = new byte[32];
        System.arraycopy(source, sourceOffset, result, result.length - sourceLength, sourceLength);
        return result;
    }

    public static String businessIdToHex(Long businessId) {
        return "0x" + toHex(businessIdToBytes32(businessId));
    }

    public static int businessTypeCode(String businessType) {
        switch (normalizeBusinessType(businessType)) {
            case "agreement":
                return 1;
            case "revenue_event":
                return 2;
            case "settlement_receipt":
                return 3;
            case "audit_checkpoint":
                return 4;
            case "work_evidence":
                return 5;
            default:
                throw new IllegalArgumentException("Unsupported business type: " + businessType);
        }
    }

    public static String normalizeBusinessType(String businessType) {
        String value = businessType == null ? "" : businessType.trim().toLowerCase();
        if ("ai_work".equals(value)) {
            return "work_evidence";
        }
        switch (value) {
            case "agreement":
            case "revenue_event":
            case "settlement_receipt":
            case "audit_checkpoint":
            case "work_evidence":
                return value;
            default:
                throw new IllegalArgumentException("Unsupported business type: " + businessType);
        }
    }

    public static String normalizeSha256(String dataHash) {
        if (dataHash == null) {
            throw new IllegalArgumentException("SHA-256 hash is required");
        }
        String value = dataHash.trim();
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("Expected a 64-character SHA-256 hexadecimal hash");
        }
        if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        return value.toLowerCase();
    }

    public static String toHex(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("Byte value is required");
        }
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    public static byte[] copy32(byte[] value) {
        if (value == null || value.length != 32) {
            throw new IllegalArgumentException("Expected exactly 32 bytes");
        }
        return Arrays.copyOf(value, value.length);
    }
}
