package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.backend.common.BusinessException;
import org.example.backend.dto.PublicEvidenceVO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.AiWorkGeneration;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.entity.Evidence;
import org.example.backend.entity.User;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.mapper.EvidenceMapper;
import org.example.backend.mapper.UserMapper;
import org.example.backend.service.BlockchainService;
import org.example.backend.service.BusinessAnchorService;
import org.example.backend.service.EvidenceService;
import org.example.backend.util.EvidenceHasher;
import org.example.backend.util.OperationLogger;
import org.example.backend.util.SigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceServiceImpl extends ServiceImpl<EvidenceMapper, Evidence> implements EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BusinessAnchorService businessAnchorService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SigningService signingService;

    @Autowired
    private AiWorkMapper workMapper;

    @Autowired
    private OperationLogger operationLogger;

    @Override
    public List<Evidence> listByWorkId(Long workId) {
        return list(new LambdaQueryWrapper<Evidence>()
                .eq(Evidence::getWorkId, workId)
                .orderByDesc(Evidence::getCreateTime));
    }

    @Override
    public Evidence verify(String dataHash) {
        return findByDataHash(dataHash);
    }

    @Override
    public Evidence findByWorkId(Long workId) {
        return getOne(new LambdaQueryWrapper<Evidence>()
                .eq(Evidence::getWorkId, workId)
                .orderByDesc(Evidence::getId)
                .last("LIMIT 1"));
    }

    @Override
    public Evidence findByDataHash(String dataHash) {
        if (dataHash == null || dataHash.trim().isEmpty()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Evidence>()
                .eq(Evidence::getDataHash, dataHash.trim().toLowerCase())
                .orderByDesc(Evidence::getId)
                .last("LIMIT 1"));
    }

    @Override
    public Evidence record(AiWork work, AiWorkGeneration generation, String canonicalJson, String dataHash) {
        Evidence existing = findByWorkId(work.getId());
        if (existing != null) {
            return ensureWorkEvidenceAnchor(existing);
        }

        Evidence evidence = new Evidence();
        evidence.setWorkId(work.getId());
        evidence.setDataHash(dataHash);
        evidence.setDataType("AI_WORK");
        evidence.setDescription(work.getTitle());
        evidence.setCanonicalPayload(canonicalJson);
        evidence.setCanonicalVersion(1);
        evidence.setContentHash(work.getFileHash());
        evidence.setChainMode(blockchainService.mode());
        evidence.setChainStatus(Evidence.CHAIN_PENDING);

        User creator = userMapper.selectById(work.getUserId());
        if (creator != null && creator.getPrivateKeyEnc() != null && !creator.getPrivateKeyEnc().isEmpty()) {
            evidence.setCreatorSignature(signingService.sign(creator.getPrivateKeyEnc(), dataHash));
        }

        // Persist local truth first so the outbox can always reference it.
        save(evidence);

        return ensureWorkEvidenceAnchor(evidence);
    }

    @Override
    public Evidence retryWorkEvidence(Long userId, Long workId) {
        AiWork work = workMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        if (userId == null || !userId.equals(work.getUserId())) {
            throw new BusinessException("Only the author can retry this evidence anchor");
        }

        Evidence evidence = findByWorkId(workId);
        if (evidence == null) {
            throw new BusinessException("Evidence not found");
        }

        ChainAnchorRecord anchor = findWorkEvidenceAnchor(evidence);
        if (anchor == null) {
            anchor = businessAnchorService.enqueue(
                    ChainAnchorRecord.TYPE_WORK_EVIDENCE,
                    work.getId(),
                    evidence.getDataHash(),
                    "work/" + work.getId() + "/evidence");
        } else {
            anchor = businessAnchorService.retry(anchor);
        }
        applyAnchorStatus(evidence, anchor);
        updateById(evidence);

        operationLogger.record(userId, "EVIDENCE_ANCHOR_RETRY",
                "workId=" + workId + " dataHash=" + evidence.getDataHash()
                        + " chainStatus=" + evidence.getChainStatus());
        return evidence;
    }

    private Evidence ensureWorkEvidenceAnchor(Evidence evidence) {
        try {
            ChainAnchorRecord anchor = businessAnchorService.enqueue(
                    ChainAnchorRecord.TYPE_WORK_EVIDENCE,
                    evidence.getWorkId(),
                    evidence.getDataHash(),
                    "work/" + evidence.getWorkId() + "/evidence");
            applyAnchorStatus(evidence, anchor);
            updateById(evidence);
        } catch (Exception e) {
            log.error("Queue evidence anchor failed, keep local evidence: hash={}",
                    evidence.getDataHash(), e);
        }
        return evidence;
    }

    private ChainAnchorRecord findWorkEvidenceAnchor(Evidence evidence) {
        List<ChainAnchorRecord> records = businessAnchorService.list(
                ChainAnchorRecord.TYPE_WORK_EVIDENCE, evidence.getWorkId());
        for (ChainAnchorRecord record : records) {
            if (record.getDataHash() != null
                    && record.getDataHash().equalsIgnoreCase(evidence.getDataHash())) {
                return record;
            }
        }
        return null;
    }

    private void applyAnchorStatus(Evidence evidence, ChainAnchorRecord anchor) {
        evidence.setChainMode(anchor.getChainMode());
        if (Integer.valueOf(ChainAnchorRecord.STATUS_ANCHORED).equals(anchor.getChainStatus())) {
            evidence.setChainStatus(Evidence.CHAIN_ANCHORED);
            evidence.setBlockchainTxHash(anchor.getBlockchainTxHash());
            evidence.setBlockNumber(anchor.getBlockNumber());
            return;
        }
        evidence.setBlockchainTxHash(null);
        evidence.setBlockNumber(null);
        if (Integer.valueOf(ChainAnchorRecord.STATUS_FAILED).equals(anchor.getChainStatus())) {
            evidence.setChainStatus(Evidence.CHAIN_FAILED);
        } else {
            evidence.setChainStatus(Evidence.CHAIN_PENDING);
        }
    }

    @Override
    public PublicEvidenceVO publicView(String dataHash) {
        Evidence evidence = findByDataHash(dataHash);
        if (evidence == null) {
            return null;
        }

        PublicEvidenceVO vo = new PublicEvidenceVO();
        vo.setDataHash(evidence.getDataHash());
        vo.setHashAlgorithm(EvidenceHasher.ALGORITHM);
        vo.setCanonicalPayload(evidence.getCanonicalPayload());
        vo.setCanonicalVersion(evidence.getCanonicalVersion());
        vo.setWorkId(evidence.getWorkId());
        vo.setFileHash(evidence.getContentHash());
        vo.setCreateTime(evidence.getCreateTime());
        vo.setChainStatus(evidence.getChainStatus());
        vo.setChainMode(evidence.getChainMode());
        vo.setBlockchainTxHash(evidence.getBlockchainTxHash());
        vo.setBlockNumber(evidence.getBlockNumber());
        vo.setChainMessage(chainMessage(evidence));

        AiWork work = workMapper.selectById(evidence.getWorkId());
        if (work != null) {
            vo.setTitle(work.getTitle());
            vo.setDescription(work.getDescription());
            vo.setFileType(work.getFileType());
            vo.setAuthorName(authorName(work.getUserId()));

            User creator = userMapper.selectById(work.getUserId());
            if (creator != null) {
                vo.setCreatorAddress(creator.getBlockchainAddress());
            }
        }
        if (evidence.getCreatorSignature() != null && work != null) {
            User creator = userMapper.selectById(work.getUserId());
            vo.setCreatorSignature(evidence.getCreatorSignature());
            vo.setSignatureVerified(creator != null && signingService.verify(
                    creator.getPublicKey(), evidence.getDataHash(), evidence.getCreatorSignature()));
        }

        vo.setGeneration(parseGeneration(evidence.getCanonicalPayload()));
        return vo;
    }

    private String authorName(Long userId) {
        if (userId == null) {
            return "匿名创作者";
        }
        User user = userMapper.selectById(userId);
        return user == null || user.getUsername() == null ? "匿名创作者" : user.getUsername();
    }

    private String chainMessage(Evidence evidence) {
        Integer status = evidence.getChainStatus();
        if (status == null) {
            return "链上状态未知";
        }
        switch (status) {
            case Evidence.CHAIN_ANCHORED:
                return "已写入区块链";
            case Evidence.CHAIN_FAILED:
                return "上链失败，可重试";
            default:
                return "已生成存证，待写入区块链";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseGeneration(String canonicalPayload) {
        if (canonicalPayload == null || canonicalPayload.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(canonicalPayload, Map.class);
            Object generation = payload.get("generation");
            return generation instanceof Map ? (Map<String, Object>) generation : new HashMap<>();
        } catch (Exception e) {
            log.warn("Parse canonical payload failed", e);
            return new HashMap<>();
        }
    }
}
