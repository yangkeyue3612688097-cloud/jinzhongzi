package org.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CollaborationAgreementVO {
    private Long id;
    private Long collaborationId;
    private Integer version;
    private String agreementHash;
    private String canonicalPayload;
    private String negotiationNote;
    private Integer status;
    private Integer chainStatus;
    private String chainMode;
    private Long createdBy;
    private LocalDateTime effectiveTime;
    private LocalDateTime createTime;
    private Integer totalRatio;
    private Integer confirmedCount;
    private Boolean allConfirmed;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long userId;
        private String userName;
        private Integer shareRatio;
        private Boolean confirmed;
        private LocalDateTime confirmedAt;
    }
}
