package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collaboration_agreement")
public class CollaborationAgreement {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_EFFECTIVE = 1;
    public static final int STATUS_SUPERSEDED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long collaborationId;
    private Integer version;
    private String agreementHash;
    private String canonicalPayload;
    private Integer status;
    private Long createdBy;
    private LocalDateTime effectiveTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
