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
@TableName("profit_record")
public class ProfitRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long collaborationId;
    private Long userId;
    private Long agreementId;
    private Long orderId;
    private Long revenueEventId;
    private BigDecimal amount;
    private String blockchainTxHash;
    private Integer chainStatus;
    private String chainMode;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
