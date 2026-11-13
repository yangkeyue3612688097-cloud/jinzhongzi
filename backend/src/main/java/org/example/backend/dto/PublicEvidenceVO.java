package org.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对外公开的验证视图，不包含文件路径、用户 ID 等内部信息。
 */
@Data
public class PublicEvidenceVO {

    private String dataHash;
    private String hashAlgorithm;
    private String canonicalPayload;
    private Integer canonicalVersion;

    private Long workId;
    private String title;
    private String description;
    private String fileType;
    private String fileHash;

    private String authorName;
    private String creatorAddress;
    private String creatorSignature;
    private boolean signatureVerified;
    private LocalDateTime createTime;

    private Integer chainStatus;
    private String chainMode;
    private String blockchainTxHash;
    private Long blockNumber;
    private String chainMessage;

    private Map<String, Object> generation;
}
