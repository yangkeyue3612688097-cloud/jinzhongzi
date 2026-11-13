package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("work_version")
public class WorkVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Integer versionNumber;
    private Long creatorId;
    private String filePath;
    private String fileHash;
    private String versionHash;
    private Long parentVersionId;
    private String changeDescription;
    private String blockchainTxHash;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
