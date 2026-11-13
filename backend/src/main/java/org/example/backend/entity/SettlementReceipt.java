package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("settlement_receipt")
public class SettlementReceipt {

    public static final int CHAIN_PENDING = 0;
    public static final int CHAIN_ANCHORED = 1;
    public static final int CHAIN_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String receiptNo;
    private Long collaborationId;
    private Long revenueEventId;
    private Long orderId;
    private Long agreementId;
    private String agreementHash;
    private Integer agreementVersion;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String receiptHash;
    private String canonicalPayload;
    private Integer chainStatus;
    private String blockchainTxHash;
    private Long blockNumber;
    private String chainMode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
