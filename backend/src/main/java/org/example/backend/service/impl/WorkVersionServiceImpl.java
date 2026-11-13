package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.BusinessException;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.entity.WorkVersion;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.mapper.WorkVersionMapper;
import org.example.backend.service.BusinessAnchorService;
import org.example.backend.service.WorkVersionService;
import org.example.backend.util.HashUtil;
import org.example.backend.util.OperationLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WorkVersionServiceImpl extends ServiceImpl<WorkVersionMapper, WorkVersion> implements WorkVersionService {

    @Autowired
    private AiWorkMapper aiWorkMapper;

    @Autowired
    private OperationLogger operationLogger;

    @Autowired
    private BusinessAnchorService businessAnchorService;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Override
    public WorkVersion createVersion(Long userId, Long workId, MultipartFile file, String changeDescription) {
        AiWork work = aiWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException("作品不存在");
        }

        if (userId == null || !userId.equals(work.getUserId())) {
            throw new BusinessException("Only the work owner can create a version");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("A version file is required");
        }

        QueryWrapper<WorkVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("work_id", workId).orderByDesc("version_number").last("LIMIT 1");
        WorkVersion latestVersion = baseMapper.selectOne(wrapper);
        int newVersionNumber = latestVersion != null ? latestVersion.getVersionNumber() + 1 : 1;

        String ext = "bin";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1);
        }
        String fileName = UUID.randomUUID().toString() + "." + ext;
        String filePath = uploadPath + File.separator + fileName;
        File dest = new File(filePath);
        try {
            dest.getParentFile().mkdirs();
            file.transferTo(dest);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }

        String normalizedDescription = changeDescription == null ? "" : changeDescription.trim();
        String fileHash = HashUtil.sha256File(dest);
        String versionHash = HashUtil.sha256(workId + "|" + newVersionNumber + "|" + fileHash
                + "|" + (latestVersion != null ? latestVersion.getId() : "")
                + "|" + normalizedDescription);

        WorkVersion version = new WorkVersion();
        version.setWorkId(workId);
        version.setVersionNumber(newVersionNumber);
        version.setCreatorId(userId);
        version.setFilePath(filePath);
        version.setFileHash(fileHash);
        version.setVersionHash(versionHash);
        version.setParentVersionId(latestVersion != null ? latestVersion.getId() : null);
        version.setChangeDescription(normalizedDescription);
        version.setStatus(1);

        baseMapper.insert(version);

        work.setCurrentVersionId(version.getId());
        aiWorkMapper.updateById(work);

        ChainAnchorRecord anchor = null;
        try {
            anchor = businessAnchorService.enqueue(
                    ChainAnchorRecord.TYPE_WORK_EVIDENCE,
                    workId,
                    versionHash,
                    "work/" + workId + "/version/" + version.getId());
            if (Integer.valueOf(ChainAnchorRecord.STATUS_ANCHORED).equals(anchor.getChainStatus())) {
                version.setBlockchainTxHash(anchor.getBlockchainTxHash());
                baseMapper.updateById(version);
            }
        } catch (Exception e) {
            log.error("Queue version evidence anchor failed: workId={}, versionId={}, hash={}",
                    workId, version.getId(), versionHash, e);
        }

        operationLogger.record(userId, "WORK_VERSION_CREATE",
                "workId=" + workId + " version=" + newVersionNumber
                        + " versionHash=" + versionHash
                        + " chainStatus=" + (anchor == null ? "queue_failed" : anchor.getChainStatus()));
        log.info("用户 {} 为作品 {} 创建了版本 {}", userId, workId, newVersionNumber);
        return version;
    }

    @Override
    public List<WorkVersion> getVersionHistory(Long workId) {
        QueryWrapper<WorkVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("work_id", workId).orderByAsc("version_number");
        return baseMapper.selectList(wrapper);
    }

    @Override
    public WorkVersion getVersionDetail(Long versionId) {
        WorkVersion version = baseMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        return version;
    }

    @Override
    public boolean confirmVersion(Long userId, Long versionId) {
        WorkVersion version = baseMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }

        AiWork work = aiWorkMapper.selectById(version.getWorkId());
        if (work == null || !work.getUserId().equals(userId)) {
            throw new BusinessException("只有作品所有者可以确认版本");
        }

        version.setStatus(2);
        boolean confirmed = baseMapper.updateById(version) > 0;
        if (confirmed) {
            operationLogger.record(userId, "WORK_VERSION_CONFIRM",
                    "workId=" + version.getWorkId() + " version=" + version.getVersionNumber()
                            + " versionHash=" + version.getVersionHash());
        }
        return confirmed;
    }

}
