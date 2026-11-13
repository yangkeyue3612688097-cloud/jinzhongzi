package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.backend.dto.WorkDTO;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.mapper.AiWorkGenerationMapper;
import org.example.backend.service.AiWorkGenerationService;
import org.springframework.stereotype.Service;

@Service
public class AiWorkGenerationServiceImpl
        extends ServiceImpl<AiWorkGenerationMapper, AiWorkGeneration>
        implements AiWorkGenerationService {

    @Override
    public AiWorkGeneration findByWorkId(Long workId) {
        return getOne(new LambdaQueryWrapper<AiWorkGeneration>()
                .eq(AiWorkGeneration::getWorkId, workId)
                .orderByDesc(AiWorkGeneration::getId)
                .last("LIMIT 1"));
    }

    @Override
    public AiWorkGeneration saveFromDto(Long workId, WorkDTO dto) {
        if (!dto.hasGenerationInfo()) {
            return null;
        }
        AiWorkGeneration generation = new AiWorkGeneration();
        generation.setWorkId(workId);
        generation.setPrompt(dto.getPrompt());
        generation.setNegativePrompt(dto.getNegativePrompt());
        generation.setModelName(dto.getModelName());
        generation.setModelVersion(dto.getModelVersion());
        generation.setSeed(dto.getSeed());
        generation.setSteps(dto.getSteps());
        generation.setCfgScale(dto.getCfgScale());
        generation.setWidth(dto.getWidth());
        generation.setHeight(dto.getHeight());
        generation.setSampler(dto.getSampler());
        generation.setExtraParams(dto.getExtraParams());
        generation.setSourceType(dto.getSourceType());
        generation.setWorkflowHash(dto.getWorkflowHash());
        generation.setRequestId(dto.getRequestId());
        generation.setScheduler(dto.getScheduler());
        save(generation);
        return generation;
    }
}
