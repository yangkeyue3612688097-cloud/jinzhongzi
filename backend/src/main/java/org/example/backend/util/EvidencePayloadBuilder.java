package org.example.backend.util;

import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把作品与生成参数整理成对外公开的存证内容。
 *
 * <p>所有标量一律转成字符串，避免 8 与 8.0、null 与空串这类差异导致哈希漂移。
 */
public final class EvidencePayloadBuilder {

    private EvidencePayloadBuilder() {
    }

    public static Map<String, Object> build(AiWork work, AiWorkGeneration generation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", EvidenceHasher.SCHEMA);
        payload.put("workId", str(work.getId()));
        payload.put("creatorId", str(work.getUserId()));
        payload.put("title", str(work.getTitle()));
        payload.put("fileType", str(work.getFileType()));
        payload.put("fileHash", str(work.getFileHash()));
        payload.put("generation", generationMap(generation));
        return payload;
    }

    private static Map<String, Object> generationMap(AiWorkGeneration generation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("prompt", str(generation == null ? null : generation.getPrompt()));
        map.put("negativePrompt", str(generation == null ? null : generation.getNegativePrompt()));
        map.put("modelName", str(generation == null ? null : generation.getModelName()));
        map.put("modelVersion", str(generation == null ? null : generation.getModelVersion()));
        map.put("seed", str(generation == null ? null : generation.getSeed()));
        map.put("steps", str(generation == null ? null : generation.getSteps()));
        map.put("cfgScale", decimal(generation == null ? null : generation.getCfgScale()));
        map.put("width", str(generation == null ? null : generation.getWidth()));
        map.put("height", str(generation == null ? null : generation.getHeight()));
        map.put("sampler", str(generation == null ? null : generation.getSampler()));
        map.put("scheduler", str(generation == null ? null : generation.getScheduler()));
        map.put("sourceType", str(generation == null ? null : generation.getSourceType()));
        map.put("workflowHash", str(generation == null ? null : generation.getWorkflowHash()));
        map.put("requestId", str(generation == null ? null : generation.getRequestId()));
        return map;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
