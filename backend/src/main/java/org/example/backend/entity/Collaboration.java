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
@TableName("collaboration")
public class Collaboration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Long ownerId;
    private String workHash;
    private String blockchainTxHash;
    private BigDecimal totalRevenue;
    private BigDecimal distributedAmount;
    private Integer status;
    private Long currentVersionId;
    private Long finalVersionId;
    private String inviteMessage;
    private Long currentAgreementId;
    private Integer chainStatus;
    private String chainMode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
