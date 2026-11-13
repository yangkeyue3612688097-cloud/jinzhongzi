package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.entity.WorkVersion;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface WorkVersionService extends IService<WorkVersion> {
    WorkVersion createVersion(Long userId, Long workId, MultipartFile file, String changeDescription);
    List<WorkVersion> getVersionHistory(Long workId);
    WorkVersion getVersionDetail(Long versionId);
    boolean confirmVersion(Long userId, Long versionId);
}
