package org.example.backend.dto;

import lombok.Data;

/**
 * Three-layer public verification result: local evidence, outbox and chain.
 */
@Data
public class PublicChainVerificationVO {

    private String businessType;
    private Long businessId;
    private String dataHash;
    private String mode;

    private boolean localEvidenceFound;
    private boolean anchorFound;
    private Integer anchorStatus;
    private String anchorMode;
    private String blockchainTxHash;
    private Long blockNumber;

    private boolean verificationAvailable;
    private boolean onChainVerified;
    private String message;
}
