package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("collaborator")
public class Collaborator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long collaborationId;
    private Long userId;
    private String userName;
    private Integer shareRatio;
    private BigDecimal contributedAmount;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
