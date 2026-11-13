package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chain_anchor")
public class ChainAnchorRecord {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ANCHORED = 1;
    public static final int STATUS_FAILED = 2;

    public static final String TYPE_AGREEMENT = "agreement";
    public static final String TYPE_REVENUE_EVENT = "revenue_event";
    public static final String TYPE_SETTLEMENT_RECEIPT = "settlement_receipt";
    public static final String TYPE_AUDIT_CHECKPOINT = "audit_checkpoint";
    public static final String TYPE_WORK_EVIDENCE = "work_evidence";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private Long businessId;
    private String dataHash;
    private String metadataUri;
    private Integer chainStatus;
    private String blockchainTxHash;
    private Long blockNumber;
    private String chainMode;
    private Integer retryCount;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
