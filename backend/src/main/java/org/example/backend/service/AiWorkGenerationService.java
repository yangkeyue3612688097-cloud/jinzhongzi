package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.dto.WorkDTO;
import org.example.backend.entity.AiWorkGeneration;

public interface AiWorkGenerationService extends IService<AiWorkGeneration> {

    AiWorkGeneration findByWorkId(Long workId);

    AiWorkGeneration saveFromDto(Long workId, WorkDTO dto);
}
