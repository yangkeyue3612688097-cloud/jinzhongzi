package org.example.backend.chain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainEncodingTest {

    @Test
    void convertsSha256IntoBytes32WithoutChangingOrder() {
        String hash = "5A9FCDD0997F16AAF773F8165B5ABB753C1860CEE98BFC74854A350C038D9609";

        byte[] encoded = ChainEncoding.sha256ToBytes32(hash);

        assertEquals(32, encoded.length);
        assertEquals("0x" + hash.toLowerCase(), ChainEncoding.sha256ToHex(hash));
        assertEquals(0x5a, encoded[0] & 0xff);
        assertEquals(0x09, encoded[31] & 0xff);
    }

    @Test
    void rejectsMalformedHashes() {
        assertThrows(IllegalArgumentException.class, () -> ChainEncoding.sha256ToBytes32(null));
        assertThrows(IllegalArgumentException.class, () -> ChainEncoding.sha256ToBytes32("abc"));
        assertThrows(IllegalArgumentException.class,
                () -> ChainEncoding.sha256ToBytes32("z" + repeat("0", 63)));
    }

    @Test
    void rightAlignsBusinessIdLikeSolidityBytes32Uint256() {
        assertEquals(
                "0x" + repeat("0", 63) + "1",
                ChainEncoding.businessIdToHex(1L)
        );
        assertEquals(
                "0x" + repeat("0", 58) + "010203",
                ChainEncoding.businessIdToHex(0x010203L)
        );
    }

    @Test
    void rejectsNegativeBusinessId() {
        assertThrows(IllegalArgumentException.class,
                () -> ChainEncoding.businessIdToBytes32(-1L));
    }

    @Test
    void mapsTheFiveBusinessFactTypes() {
        assertEquals(1, ChainEncoding.businessTypeCode("agreement"));
        assertEquals(2, ChainEncoding.businessTypeCode("revenue_event"));
        assertEquals(3, ChainEncoding.businessTypeCode("settlement_receipt"));
        assertEquals(4, ChainEncoding.businessTypeCode("audit_checkpoint"));
        assertEquals(5, ChainEncoding.businessTypeCode("work_evidence"));
        assertEquals(5, ChainEncoding.businessTypeCode("AI_WORK"));
        assertEquals("work_evidence", ChainEncoding.normalizeBusinessType("AI_WORK"));
        assertEquals("agreement", ChainEncoding.normalizeBusinessType(" AGREEMENT "));
        assertThrows(IllegalArgumentException.class,
                () -> ChainEncoding.businessTypeCode("unknown"));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
