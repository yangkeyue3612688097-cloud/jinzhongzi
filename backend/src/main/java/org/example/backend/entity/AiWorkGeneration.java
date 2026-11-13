package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 生成参数快照。一个作品对应一条记录，是存证内容的核心来源。
 */
@Data
@TableName("ai_work_generation")
public class AiWorkGeneration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workId;
    private String prompt;
    private String negativePrompt;
    private String modelName;
    private String modelVersion;
    private String seed;
    private Integer steps;
    private BigDecimal cfgScale;
    private Integer width;
    private Integer height;
    private String sampler;
    private String extraParams;
    private String sourceType;
    private String workflowHash;
    private String requestId;
    private String scheduler;
    private LocalDateTime generatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
