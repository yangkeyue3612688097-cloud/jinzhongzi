package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("license_order")
public class LicenseOrder {

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_SETTLED = 2;
    public static final int STATUS_CANCELLED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long workId;
    private Long collaborationId;
    private String buyerName;
    private BigDecimal amount;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
