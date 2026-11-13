package org.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.dto.ComfyuiImportDTO;
import org.example.backend.dto.WorkDTO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.entity.Evidence;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AiWorkService extends IService<AiWork> {

    AiWork createWork(Long userId, WorkDTO dto, MultipartFile file);

    IPage<AiWork> listWorks(Long userId, int page, int size);

    AiWork getWorkDetail(Long workId);

    AiWorkGeneration getGeneration(Long workId);

    Map<String, Object> importComfyui(Long userId, ComfyuiImportDTO dto, MultipartFile file);

    /**
     * 生成作品的存证（幂等：重复调用返回同一条证据）。
     */
    Evidence evidence(Long userId, Long workId);
}
