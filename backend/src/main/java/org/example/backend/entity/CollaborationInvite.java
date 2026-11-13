package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collaboration_invite")
public class CollaborationInvite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Long inviterId;
    private Long inviteeId;
    private String message;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
