package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.dto.PublicEvidenceVO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.entity.Evidence;

import java.util.List;

public interface EvidenceService extends IService<Evidence> {

    List<Evidence> listByWorkId(Long workId);

    Evidence verify(String dataHash);

    Evidence findByWorkId(Long workId);

    Evidence findByDataHash(String dataHash);

    /**
     * 为作品生成存证：规范化内容 -> 计算哈希 -> 本地留存 -> 尝试上链。
     */
    Evidence record(AiWork work, AiWorkGeneration generation, String canonicalJson, String dataHash);

    Evidence retryWorkEvidence(Long userId, Long workId);

    PublicEvidenceVO publicView(String dataHash);
}
