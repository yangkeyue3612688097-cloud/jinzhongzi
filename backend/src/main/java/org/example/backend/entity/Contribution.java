package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("contribution")
public class Contribution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private Long userId;
    private String contributionType;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
