package org.example.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ComfyUI collector posts this together with the generated file. Values are read
 * straight from the ComfyUI /history response, so they bind a real generation to
 * the uploaded artifact instead of being keyed in by hand.
 */
@Data
public class ComfyuiImportDTO {

    private String title;
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
    private String scheduler;
    private String workflowHash;
    private String requestId;
    private String workflowRaw;
    private String generatedAt;
}
