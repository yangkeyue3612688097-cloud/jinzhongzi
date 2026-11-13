package org.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.backend.common.Result;
import org.example.backend.dto.ComfyuiImportDTO;
import org.example.backend.dto.WorkDTO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.entity.Evidence;
import org.example.backend.service.AiWorkService;
import org.example.backend.service.EvidenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/work")
public class AiWorkController {

    @Autowired
    private AiWorkService aiWorkService;

    @Autowired
    private EvidenceService evidenceService;

    @PostMapping("/create")
    public Result<AiWork> createWork(HttpServletRequest request,
                                     @Valid WorkDTO dto,
                                     @RequestParam(value = "file", required = false) MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aiWorkService.createWork(userId, dto, file));
    }

    @PostMapping(value = "/import/comfyui", consumes = "multipart/form-data")
    public Result<Map<String, Object>> importComfyui(HttpServletRequest request,
                                                     @RequestPart("metadata") ComfyuiImportDTO dto,
                                                     @RequestPart("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aiWorkService.importComfyui(userId, dto, file));
    }

    @GetMapping("/list")
    public Result<IPage<AiWork>> listWorks(HttpServletRequest request,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aiWorkService.listWorks(userId, page, size));
    }

    @GetMapping("/detail/{id}")
    public Result<AiWork> getWorkDetail(@PathVariable Long id) {
        return Result.success(aiWorkService.getWorkDetail(id));
    }

    @GetMapping("/generation/{workId}")
    public Result<AiWorkGeneration> getGeneration(@PathVariable Long workId) {
        return Result.success(aiWorkService.getGeneration(workId));
    }

    /**
     * 生成存证。链上合约接入前，证据在本地落库并标记为待上链。
     */
    @PostMapping("/evidence/{id}")
    public Result<Evidence> evidence(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aiWorkService.evidence(userId, id));
    }

    @PostMapping("/evidence/{workId}/anchor/retry")
    public Result<Evidence> retryEvidenceAnchor(HttpServletRequest request,
                                                @PathVariable Long workId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(evidenceService.retryWorkEvidence(userId, workId));
    }

    @GetMapping("/evidence/list/{workId}")
    public Result<List<Evidence>> listEvidence(@PathVariable Long workId) {
        return Result.success(evidenceService.listByWorkId(workId));
    }

    @GetMapping("/evidence/verify")
    public Result<Evidence> verifyEvidence(@RequestParam String dataHash) {
        return Result.success(evidenceService.verify(dataHash));
    }
}
