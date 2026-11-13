package org.example.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collaboration_agreement_confirmation")
public class AgreementConfirmation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agreementId;
    private Long userId;
    private String confirmationHash;
    private String signature;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
