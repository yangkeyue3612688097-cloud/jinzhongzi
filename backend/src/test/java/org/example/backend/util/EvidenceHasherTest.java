package org.example.backend.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EvidenceHasherTest {

    @Test
    void canonicalJsonIsIndependentOfInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("title", "Test");
        first.put("workId", "12");
        first.put("fileHash", "abc123");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("fileHash", "abc123");
        second.put("title", "Test");
        second.put("workId", "12");

        assertEquals(EvidenceHasher.canonicalJson(first), EvidenceHasher.canonicalJson(second));
        assertEquals(EvidenceHasher.hash(EvidenceHasher.canonicalJson(first)),
                EvidenceHasher.hash(EvidenceHasher.canonicalJson(second)));
    }

    @Test
    void canonicalJsonHasNoWhitespaceAndHashIsStable() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", EvidenceHasher.SCHEMA);
        payload.put("workId", "12");

        String json = EvidenceHasher.canonicalJson(payload);

        assertFalse(json.contains(" "));
        assertEquals("{\"schema\":\"aic-evidence/v1\",\"workId\":\"12\"}", json);
        assertEquals(64, EvidenceHasher.hash(json).length());
        assertEquals(EvidenceHasher.hash(json), EvidenceHasher.hash(json));
    }
}
