package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evidence")
public class Evidence {

    /** 链上状态：0 待上链，1 已上链，2 上链失败 */
    public static final int CHAIN_PENDING = 0;
    public static final int CHAIN_ANCHORED = 1;
    public static final int CHAIN_FAILED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workId;
    private String dataHash;
    private String dataType;
    private String description;
    private String blockchainTxHash;
    private Long blockNumber;
    private Integer chainStatus;
    private String chainMode;
    private Integer canonicalVersion;
    private String contentHash;
    private String canonicalPayload;
    private String creatorSignature;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
