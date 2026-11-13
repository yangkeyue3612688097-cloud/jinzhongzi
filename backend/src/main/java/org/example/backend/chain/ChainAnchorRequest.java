package org.example.backend.chain;

/**
 * Chain-neutral request for anchoring one business fact.
 * The adapter converts the business id and SHA-256 hash into bytes32 values.
 */
public class ChainAnchorRequest {

    private final Long businessId;
    private final String dataHash;
    private final String businessType;
    private final String metadataUri;

    public ChainAnchorRequest(Long businessId, String dataHash, String businessType, String metadataUri) {
        if (businessId == null) {
            throw new IllegalArgumentException("Business id is required");
        }
        if (dataHash == null || dataHash.trim().isEmpty()) {
            throw new IllegalArgumentException("Data hash is required");
        }
        if (businessType == null || businessType.trim().isEmpty()) {
            throw new IllegalArgumentException("Business type is required");
        }
        this.businessId = businessId;
        this.dataHash = dataHash.trim();
        this.businessType = businessType.trim();
        this.metadataUri = metadataUri == null ? "" : metadataUri.trim();
    }

    public Long getBusinessId() {
        return businessId;
    }

    public String getDataHash() {
        return dataHash;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getMetadataUri() {
        return metadataUri;
    }
}
