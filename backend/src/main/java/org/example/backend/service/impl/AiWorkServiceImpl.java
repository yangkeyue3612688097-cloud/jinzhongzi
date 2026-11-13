package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.backend.common.BusinessException;
import org.example.backend.dto.ComfyuiImportDTO;
import org.example.backend.dto.WorkDTO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.entity.Evidence;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.service.AiWorkGenerationService;
import org.example.backend.service.AiWorkService;
import org.example.backend.service.EvidenceService;
import org.example.backend.util.EvidenceHasher;
import org.example.backend.util.EvidencePayloadBuilder;
import org.example.backend.util.HashUtil;
import org.example.backend.util.OperationLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AiWorkServiceImpl extends ServiceImpl<AiWorkMapper, AiWork> implements AiWorkService {

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private AiWorkGenerationService generationService;

    @Autowired
    private OperationLogger operationLogger;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public AiWork createWork(Long userId, WorkDTO dto, MultipartFile file) {
        AiWork work = new AiWork();
        work.setUserId(userId);
        work.setTitle(dto.getTitle());
        work.setDescription(dto.getDescription());
        work.setFileType(dto.getFileType());
        work.setStatus(0);
        work.setDeleted(0);

        if (file != null && !file.isEmpty()) {
            work.setFilePath(storeFile(file));
            work.setFileHash(HashUtil.sha256File(new File(work.getFilePath())));
        }

        save(work);
        generationService.saveFromDto(work.getId(), dto);
        operationLogger.record(userId, "WORK_CREATE", "workId=" + work.getId());
        return work;
    }

    @Override
    public IPage<AiWork> listWorks(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<AiWork>()
                .eq(AiWork::getUserId, userId)
                .orderByDesc(AiWork::getCreateTime));
    }

    @Override
    public AiWork getWorkDetail(Long workId) {
        AiWork work = getById(workId);
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        return work;
    }

    @Override
    public AiWorkGeneration getGeneration(Long workId) {
        return generationService.findByWorkId(workId);
    }

    @Override
    public Evidence evidence(Long userId, Long workId) {
        AiWork work = getById(workId);
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        if (userId == null || !userId.equals(work.getUserId())) {
            throw new BusinessException("Only the author can create evidence for this work");
        }

        Evidence existing = evidenceService.findByWorkId(workId);
        if (existing != null) {
            return existing;
        }

        if (work.getFileHash() == null || work.getFileHash().isEmpty()) {
            throw new BusinessException("Work has no file content to attest");
        }

        AiWorkGeneration generation = generationService.findByWorkId(workId);
        Map<String, Object> payload = EvidencePayloadBuilder.build(work, generation);
        String canonicalJson = EvidenceHasher.canonicalJson(payload);
        String dataHash = EvidenceHasher.hash(canonicalJson);

        Evidence evidence = evidenceService.record(work, generation, canonicalJson, dataHash);

        work.setStatus(1);
        updateById(work);
        operationLogger.record(userId, "EVIDENCE_CREATE", "workId=" + workId + " dataHash=" + dataHash);
        return evidence;
    }

    @Override
    public Map<String, Object> importComfyui(Long userId, ComfyuiImportDTO dto, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Generated file is required");
        }

        AiWork work = new AiWork();
        work.setUserId(userId);
        work.setTitle(dto.getTitle() == null || dto.getTitle().trim().isEmpty()
                ? "ComfyUI-" + shortId(dto.getRequestId())
                : dto.getTitle().trim());
        work.setDescription(dto.getPrompt());
        work.setFileType("image");
        work.setStatus(0);
        work.setDeleted(0);
        work.setFilePath(storeFile(file));
        work.setFileHash(HashUtil.sha256File(new File(work.getFilePath())));
        save(work);

        AiWorkGeneration generation = new AiWorkGeneration();
        generation.setWorkId(work.getId());
        generation.setSourceType("comfyui");
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
        generation.setScheduler(dto.getScheduler());
        generation.setWorkflowHash(dto.getWorkflowHash());
        generation.setRequestId(dto.getRequestId());
        generation.setExtraParams(dto.getWorkflowRaw());
        generation.setGeneratedAt(parseDateTime(dto.getGeneratedAt()));
        generationService.save(generation);

        Evidence evidence = evidence(userId, work.getId());

        operationLogger.record(userId, "WORK_IMPORT_COMFYUI",
                "workId=" + work.getId() + " requestId=" + dto.getRequestId());

        Map<String, Object> result = new HashMap<>();
        result.put("work", work);
        result.put("generation", generation);
        result.put("evidence", evidence);
        return result;
    }

    private String shortId(String value) {
        return value == null || value.length() <= 8 ? value : value.substring(0, 8);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String storeFile(MultipartFile file) {
        try {
            File dir = new File(uploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BusinessException("Upload directory is not writable");
            }
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : "";
            File dest = new File(dir, UUID.randomUUID().toString().replace("-", "") + ext);
            file.transferTo(dest);
            return dest.getAbsolutePath();
        } catch (IOException e) {
            throw new BusinessException("File upload failed");
        }
    }
}
