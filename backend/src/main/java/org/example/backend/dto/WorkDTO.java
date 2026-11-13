package org.example.backend.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class WorkDTO {

    @NotBlank(message = "title required")
    private String title;

    private String description;
    private String fileType;

    // 生成参数，用于构建可复现的存证内容
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

    public boolean hasGenerationInfo() {
        return notBlank(prompt) || notBlank(negativePrompt) || notBlank(modelName)
                || notBlank(modelVersion) || notBlank(seed) || steps != null
                || cfgScale != null || width != null || height != null
                || notBlank(sampler) || notBlank(extraParams)
                || notBlank(workflowHash) || notBlank(requestId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
