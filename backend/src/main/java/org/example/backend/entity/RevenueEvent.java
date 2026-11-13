package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("revenue_event")
public class RevenueEvent {

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_SETTLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long collaborationId;
    private Long workId;
    private Long agreementId;
    private String agreementHash;
    private Integer agreementVersion;
    private String eventHash;
    private BigDecimal amount;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
