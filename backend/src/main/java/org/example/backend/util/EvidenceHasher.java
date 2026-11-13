package org.example.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 证据内容的规范化与哈希。
 *
 * <p>规则固定为：按键名升序重新排列 -> 输出无空白 JSON -> UTF-8 编码 -> SHA-256。
 * 同样的数据在任何机器、任何语言下都应得到同一个 dataHash，否则第三方无法复算。
 */
public final class EvidenceHasher {

    public static final String SCHEMA = "aic-evidence/v1";
    public static final String ALGORITHM = "SHA-256(canonical-json-utf8)";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvidenceHasher() {
    }

    public static String canonicalJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(sort(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build canonical json", e);
        }
    }

    public static String hash(String canonicalJson) {
        return HashUtil.sha256(canonicalJson);
    }

    @SuppressWarnings("unchecked")
    private static Object sort(Object value) {
        if (value instanceof Map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                sorted.put(String.valueOf(entry.getKey()), sort(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof Collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                list.add(sort(item));
            }
            return list;
        }
        return value;
    }
}
