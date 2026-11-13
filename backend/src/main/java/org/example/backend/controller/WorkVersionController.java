package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.Result;
import org.example.backend.entity.WorkVersion;
import org.example.backend.service.WorkVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/work/version")
public class WorkVersionController {

    @Autowired
    private WorkVersionService workVersionService;

    @PostMapping("/create")
    public Result<WorkVersion> createVersion(
            HttpServletRequest request,
            @RequestParam Long workId,
            @RequestParam(required = false) String changeDescription,
            @RequestParam("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        WorkVersion version = workVersionService.createVersion(userId, workId, file, changeDescription);
        return Result.success(version);
    }

    @GetMapping("/history/{workId}")
    public Result<List<WorkVersion>> getVersionHistory(@PathVariable Long workId) {
        return Result.success(workVersionService.getVersionHistory(workId));
    }

    @GetMapping("/detail/{versionId}")
    public Result<WorkVersion> getVersionDetail(@PathVariable Long versionId) {
        return Result.success(workVersionService.getVersionDetail(versionId));
    }

    @PostMapping("/confirm/{versionId}")
    public Result<Boolean> confirmVersion(HttpServletRequest request, @PathVariable Long versionId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(workVersionService.confirmVersion(userId, versionId));
    }
}
