package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.common.BusinessException;
import org.example.backend.dto.AgreementDraftDTO;
import org.example.backend.dto.AgreementItemDTO;
import org.example.backend.dto.CollaborationAgreementVO;
import org.example.backend.entity.AgreementConfirmation;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.CollaborationAgreement;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.User;
import org.example.backend.mapper.AgreementConfirmationMapper;
import org.example.backend.mapper.CollaborationAgreementMapper;
import org.example.backend.mapper.CollaborationMapper;
import org.example.backend.mapper.CollaboratorMapper;
import org.example.backend.mapper.UserMapper;
import org.example.backend.service.BusinessAnchorService;
import org.example.backend.service.CollaborationAgreementService;
import org.example.backend.util.EvidenceHasher;
import org.example.backend.util.HashUtil;
import org.example.backend.util.OperationLogger;
import org.example.backend.util.SigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CollaborationAgreementServiceImpl implements CollaborationAgreementService {

    private static final Logger log = LoggerFactory.getLogger(CollaborationAgreementServiceImpl.class);
    private static final String SCHEMA = "aic-collaboration-agreement/v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private CollaborationMapper collaborationMapper;

    @Autowired
    private CollaboratorMapper collaboratorMapper;

    @Autowired
    private CollaborationAgreementMapper agreementMapper;

    @Autowired
    private AgreementConfirmationMapper confirmationMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SigningService signingService;

    @Autowired
    private OperationLogger operationLogger;

    @Autowired
    private BusinessAnchorService businessAnchorService;

    @Override
    @Transactional
    public CollaborationAgreementVO createDraft(Long userId, Long collaborationId, AgreementDraftDTO dto) {
        Collaboration collaboration = requireCollaboration(collaborationId);
        if (!collaboration.getOwnerId().equals(userId)) {
            throw new BusinessException("Only the collaboration owner can draft an agreement");
        }

        List<Collaborator> collaborators = activeCollaborators(collaborationId);
        Map<Long, Collaborator> activeByUser = collaborators.stream()
                .collect(Collectors.toMap(Collaborator::getUserId, item -> item));

        List<AgreementItemDTO> requested = normalizeAndValidateItems(dto.getItems(), activeByUser.keySet());
        int totalRatio = requested.stream().mapToInt(AgreementItemDTO::getShareRatio).sum();
        if (totalRatio != 100) {
            throw new BusinessException("Share ratios must add up to 100%, current total is " + totalRatio + "%");
        }

        Integer latestVersion = nextVersion(collaborationId);
        supersedePendingAgreements(collaborationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", SCHEMA);
        payload.put("collaborationId", String.valueOf(collaborationId));
        payload.put("workId", String.valueOf(collaboration.getWorkId()));
        payload.put("workHash", value(collaboration.getWorkHash()));
        payload.put("version", String.valueOf(latestVersion));
        payload.put("negotiationNote", value(dto.getNegotiationNote()));
        payload.put("items", requested.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", String.valueOf(item.getUserId()));
            row.put("shareRatio", String.valueOf(item.getShareRatio()));
            return row;
        }).collect(Collectors.toList()));

        String canonicalJson = EvidenceHasher.canonicalJson(payload);
        CollaborationAgreement agreement = new CollaborationAgreement();
        agreement.setCollaborationId(collaborationId);
        agreement.setVersion(latestVersion);
        agreement.setAgreementHash(EvidenceHasher.hash(canonicalJson));
        agreement.setCanonicalPayload(canonicalJson);
        agreement.setStatus(CollaborationAgreement.STATUS_PENDING);
        agreement.setCreatedBy(userId);
        agreementMapper.insert(agreement);

        // Creating the draft is the owner's act of confirmation.
        confirmInternal(agreement, userId);
        evaluateActivation(agreement);

        operationLogger.record(userId, "AGREEMENT_DRAFT",
                "collaborationId=" + collaborationId + " version=" + latestVersion);
        log.info("User {} created collaboration agreement {}: hash={}", userId, agreement.getId(), agreement.getAgreementHash());
        return toVO(agreementMapper.selectById(agreement.getId()));
    }

    @Override
    @Transactional
    public CollaborationAgreementVO confirm(Long userId, Long agreementId) {
        CollaborationAgreement agreement = agreementMapper.selectById(agreementId);
        if (agreement == null) {
            throw new BusinessException("Agreement not found");
        }
        if (agreement.getStatus() == CollaborationAgreement.STATUS_SUPERSEDED) {
            throw new BusinessException("Agreement has been superseded");
        }

        List<AgreementItemDTO> items = readItems(agreement);
        boolean participant = items.stream().anyMatch(item -> item.getUserId().equals(userId));
        if (!participant) {
            throw new BusinessException("Only listed collaborators can confirm this agreement");
        }
        if (agreement.getStatus() == CollaborationAgreement.STATUS_EFFECTIVE) {
            return toVO(agreement);
        }

        confirmInternal(agreement, userId);
        evaluateActivation(agreement);
        operationLogger.record(userId, "AGREEMENT_CONFIRM", "agreementId=" + agreementId);
        return toVO(agreementMapper.selectById(agreement.getId()));
    }

    @Override
    public CollaborationAgreementVO getDetail(Long agreementId) {
        CollaborationAgreement agreement = agreementMapper.selectById(agreementId);
        if (agreement == null) {
            throw new BusinessException("Agreement not found");
        }
        return toVO(agreement);
    }

    @Override
    public CollaborationAgreementVO getCurrent(Long collaborationId) {
        CollaborationAgreement agreement = agreementMapper.selectOne(
                new LambdaQueryWrapper<CollaborationAgreement>()
                        .eq(CollaborationAgreement::getCollaborationId, collaborationId)
                        .in(CollaborationAgreement::getStatus,
                                CollaborationAgreement.STATUS_PENDING,
                                CollaborationAgreement.STATUS_EFFECTIVE)
                        .orderByDesc(CollaborationAgreement::getId)
                        .last("LIMIT 1"));
        return agreement == null ? null : toVO(agreement);
    }

    @Override
    public CollaborationAgreementVO getEffectiveVO(Long collaborationId) {
        return toVO(getEffective(collaborationId));
    }

    @Override
    public List<CollaborationAgreementVO> history(Long collaborationId) {
        return agreementMapper.selectList(
                        new LambdaQueryWrapper<CollaborationAgreement>()
                                .eq(CollaborationAgreement::getCollaborationId, collaborationId)
                                .orderByDesc(CollaborationAgreement::getVersion))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CollaborationAgreement getEffective(Long collaborationId) {
        return agreementMapper.selectOne(
                new LambdaQueryWrapper<CollaborationAgreement>()
                        .eq(CollaborationAgreement::getCollaborationId, collaborationId)
                        .eq(CollaborationAgreement::getStatus, CollaborationAgreement.STATUS_EFFECTIVE)
                        .orderByDesc(CollaborationAgreement::getId)
                        .last("LIMIT 1"));
    }

    @Override
    public CollaborationAgreement getAgreement(Long agreementId) {
        return agreementMapper.selectById(agreementId);
    }

    @Override
    public List<AgreementItemDTO> readItems(CollaborationAgreement agreement) {
        if (agreement == null || agreement.getCanonicalPayload() == null) {
            throw new BusinessException("Agreement payload is missing");
        }
        try {
            JsonNode root = MAPPER.readTree(agreement.getCanonicalPayload());
            List<AgreementItemDTO> items = new ArrayList<>();
            for (JsonNode node : root.path("items")) {
                AgreementItemDTO item = new AgreementItemDTO();
                item.setUserId(Long.parseLong(node.path("userId").asText()));
                item.setShareRatio(Integer.parseInt(node.path("shareRatio").asText()));
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            throw new BusinessException("Agreement payload cannot be parsed");
        }
    }

    private Collaboration requireCollaboration(Long collaborationId) {
        Collaboration collaboration = collaborationMapper.selectById(collaborationId);
        if (collaboration == null) {
            throw new BusinessException("Collaboration not found");
        }
        return collaboration;
    }

    private List<Collaborator> activeCollaborators(Long collaborationId) {
        return collaboratorMapper.selectList(new LambdaQueryWrapper<Collaborator>()
                .eq(Collaborator::getCollaborationId, collaborationId)
                .eq(Collaborator::getStatus, 1)
                .orderByAsc(Collaborator::getUserId));
    }

    private List<AgreementItemDTO> normalizeAndValidateItems(List<AgreementItemDTO> items, Set<Long> collaboratorIds) {
        Map<Long, AgreementItemDTO> unique = new LinkedHashMap<>();
        for (AgreementItemDTO item : items) {
            if (item == null || item.getUserId() == null || item.getShareRatio() == null) {
                throw new BusinessException("Every agreement item needs a user and share ratio");
            }
            if (item.getShareRatio() <= 0) {
                throw new BusinessException("Every collaborator must have a share greater than 0%");
            }
            if (unique.put(item.getUserId(), item) != null) {
                throw new BusinessException("Duplicate collaborator in agreement");
            }
        }
        if (!unique.keySet().equals(new HashSet<>(collaboratorIds))) {
            throw new BusinessException("Agreement must include every active collaborator exactly once");
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(AgreementItemDTO::getUserId))
                .collect(Collectors.toList());
    }

    private Integer nextVersion(Long collaborationId) {
        CollaborationAgreement latest = agreementMapper.selectOne(
                new LambdaQueryWrapper<CollaborationAgreement>()
                        .eq(CollaborationAgreement::getCollaborationId, collaborationId)
                        .orderByDesc(CollaborationAgreement::getVersion)
                        .last("LIMIT 1"));
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    /**
     * A new negotiation supersedes only other drafts. The effective agreement
     * remains current until the replacement has been confirmed by everyone.
     */
    private void supersedePendingAgreements(Long collaborationId) {
        agreementMapper.update(null, new LambdaUpdateWrapper<CollaborationAgreement>()
                .eq(CollaborationAgreement::getCollaborationId, collaborationId)
                .eq(CollaborationAgreement::getStatus, CollaborationAgreement.STATUS_PENDING)
                .set(CollaborationAgreement::getStatus, CollaborationAgreement.STATUS_SUPERSEDED));
    }

    private void confirmInternal(CollaborationAgreement agreement, Long userId) {
        Long existing = confirmationMapper.selectCount(new LambdaQueryWrapper<AgreementConfirmation>()
                .eq(AgreementConfirmation::getAgreementId, agreement.getId())
                .eq(AgreementConfirmation::getUserId, userId));
        if (existing != null && existing > 0) {
            return;
        }
        AgreementConfirmation confirmation = new AgreementConfirmation();
        confirmation.setAgreementId(agreement.getId());
        confirmation.setUserId(userId);
        String confirmationPayload = "aic-collaboration-confirmation/v1|" + agreement.getAgreementHash() + "|" + userId;
        confirmation.setConfirmationHash(HashUtil.sha256(confirmationPayload));
        User user = userMapper.selectById(userId);
        if (user != null && user.getPrivateKeyEnc() != null && !user.getPrivateKeyEnc().isEmpty()) {
            confirmation.setSignature(signingService.sign(user.getPrivateKeyEnc(), confirmationPayload));
        }
        confirmationMapper.insert(confirmation);
    }

    @Transactional
    protected void evaluateActivation(CollaborationAgreement agreement) {
        List<AgreementItemDTO> items = readItems(agreement);
        Long confirmed = confirmationMapper.selectCount(new LambdaQueryWrapper<AgreementConfirmation>()
                .eq(AgreementConfirmation::getAgreementId, agreement.getId()));
        if (confirmed == null || confirmed < items.size()) {
            return;
        }

        agreementMapper.update(null, new LambdaUpdateWrapper<CollaborationAgreement>()
                .eq(CollaborationAgreement::getCollaborationId, agreement.getCollaborationId())
                .ne(CollaborationAgreement::getId, agreement.getId())
                .in(CollaborationAgreement::getStatus,
                        CollaborationAgreement.STATUS_PENDING,
                        CollaborationAgreement.STATUS_EFFECTIVE)
                .set(CollaborationAgreement::getStatus, CollaborationAgreement.STATUS_SUPERSEDED));

        agreement.setStatus(CollaborationAgreement.STATUS_EFFECTIVE);
        agreement.setEffectiveTime(LocalDateTime.now());
        agreementMapper.updateById(agreement);

        Collaboration collaboration = collaborationMapper.selectById(agreement.getCollaborationId());
        if (collaboration != null) {
            collaboration.setCurrentAgreementId(agreement.getId());
            collaborationMapper.updateById(collaboration);
        }
        businessAnchorService.enqueue(
                ChainAnchorRecord.TYPE_AGREEMENT,
                agreement.getId(),
                agreement.getAgreementHash(),
                "collaboration/" + agreement.getCollaborationId() + "/agreement/" + agreement.getId());
        log.info("Collaboration agreement {} is now effective", agreement.getId());
    }

    private CollaborationAgreementVO toVO(CollaborationAgreement agreement) {
        if (agreement == null) {
            return null;
        }
        List<AgreementItemDTO> items = readItems(agreement);
        List<AgreementConfirmation> confirmations = confirmationMapper.selectList(
                new LambdaQueryWrapper<AgreementConfirmation>()
                        .eq(AgreementConfirmation::getAgreementId, agreement.getId()));
        Map<Long, LocalDateTime> confirmedAt = confirmations.stream()
                .collect(Collectors.toMap(AgreementConfirmation::getUserId,
                        AgreementConfirmation::getCreateTime, (left, right) -> left));

        Map<Long, String> names = new HashMap<>();
        if (!items.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(items.stream()
                    .map(AgreementItemDTO::getUserId)
                    .collect(Collectors.toList()));
            names.putAll(users.stream().collect(Collectors.toMap(User::getId, User::getUsername)));
        }

        CollaborationAgreementVO vo = new CollaborationAgreementVO();
        vo.setId(agreement.getId());
        vo.setCollaborationId(agreement.getCollaborationId());
        vo.setVersion(agreement.getVersion());
        vo.setAgreementHash(agreement.getAgreementHash());
        vo.setCanonicalPayload(agreement.getCanonicalPayload());
        vo.setNegotiationNote(readNegotiationNote(agreement));
        vo.setStatus(agreement.getStatus());
        vo.setCreatedBy(agreement.getCreatedBy());
        vo.setEffectiveTime(agreement.getEffectiveTime());
        vo.setCreateTime(agreement.getCreateTime());
        vo.setTotalRatio(items.stream().mapToInt(AgreementItemDTO::getShareRatio).sum());
        vo.setConfirmedCount(confirmations.size());
        vo.setAllConfirmed(agreement.getStatus() == CollaborationAgreement.STATUS_EFFECTIVE
                || confirmations.size() >= items.size());

        List<ChainAnchorRecord> anchors = businessAnchorService.list(
                ChainAnchorRecord.TYPE_AGREEMENT, agreement.getId());
        if (!anchors.isEmpty()) {
            vo.setChainStatus(anchors.get(0).getChainStatus());
            vo.setChainMode(anchors.get(0).getChainMode());
        }

        List<CollaborationAgreementVO.Item> rows = items.stream().map(item -> {
            CollaborationAgreementVO.Item row = new CollaborationAgreementVO.Item();
            row.setUserId(item.getUserId());
            row.setUserName(names.getOrDefault(item.getUserId(), "User " + item.getUserId()));
            row.setShareRatio(item.getShareRatio());
            row.setConfirmed(confirmedAt.containsKey(item.getUserId()));
            row.setConfirmedAt(confirmedAt.get(item.getUserId()));
            return row;
        }).collect(Collectors.toList());
        vo.setItems(rows);
        return vo;
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }

    private String readNegotiationNote(CollaborationAgreement agreement) {
        if (agreement == null || agreement.getCanonicalPayload() == null) {
            return "";
        }
        try {
            return MAPPER.readTree(agreement.getCanonicalPayload()).path("negotiationNote").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
