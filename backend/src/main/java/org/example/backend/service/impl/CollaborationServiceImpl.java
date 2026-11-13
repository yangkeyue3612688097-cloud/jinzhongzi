package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.BusinessException;
import org.example.backend.dto.AgreementItemDTO;
import org.example.backend.dto.CollaboratorDTO;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.CollaborationAgreement;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.LicenseOrder;
import org.example.backend.entity.ProfitRecord;
import org.example.backend.entity.RevenueEvent;
import org.example.backend.entity.SettlementReceipt;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.mapper.CollaborationMapper;
import org.example.backend.mapper.CollaboratorMapper;
import org.example.backend.mapper.LicenseOrderMapper;
import org.example.backend.mapper.ProfitRecordMapper;
import org.example.backend.mapper.RevenueEventMapper;
import org.example.backend.mapper.SettlementReceiptMapper;
import org.example.backend.service.BlockchainService;
import org.example.backend.service.BusinessAnchorService;
import org.example.backend.service.CollaborationAgreementService;
import org.example.backend.service.CollaborationService;
import org.example.backend.util.OperationLogger;
import org.example.backend.util.EvidenceHasher;
import org.example.backend.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollaborationServiceImpl extends ServiceImpl<CollaborationMapper, Collaboration> implements CollaborationService {

    @Autowired
    private CollaboratorMapper collaboratorMapper;

    @Autowired
    private AiWorkMapper aiWorkMapper;

    @Autowired
    private ProfitRecordMapper profitRecordMapper;

    @Autowired
    private LicenseOrderMapper licenseOrderMapper;

    @Autowired
    private RevenueEventMapper revenueEventMapper;

    @Autowired
    private SettlementReceiptMapper settlementReceiptMapper;

    @Autowired
    private CollaborationAgreementService agreementService;

    @Autowired
    private BusinessAnchorService businessAnchorService;

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private OperationLogger operationLogger;

    @Override
    @Transactional
    public Collaboration createCollaboration(Long userId, Long workId) {
        AiWork work = aiWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        if (!work.getUserId().equals(userId)) {
            throw new BusinessException("Only the work owner can create a collaboration");
        }
        if (work.getFileHash() == null || work.getFileHash().trim().isEmpty()) {
            throw new BusinessException("Work has no file hash");
        }

        QueryWrapper<Collaboration> wrapper = new QueryWrapper<>();
        wrapper.eq("work_id", workId).eq("owner_id", userId);
        Collaboration existing = baseMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException("A collaboration already exists for this work");
        }

        Collaboration collaboration = new Collaboration();
        collaboration.setWorkId(workId);
        collaboration.setOwnerId(userId);
        collaboration.setWorkHash(work.getFileHash());
        collaboration.setBlockchainTxHash(null);
        collaboration.setChainStatus(0);
        collaboration.setChainMode(blockchainService.mode());
        collaboration.setTotalRevenue(BigDecimal.ZERO);
        collaboration.setDistributedAmount(BigDecimal.ZERO);
        collaboration.setStatus(1);
        baseMapper.insert(collaboration);

        Collaborator ownerCollaborator = new Collaborator();
        ownerCollaborator.setCollaborationId(collaboration.getId());
        ownerCollaborator.setUserId(userId);
        ownerCollaborator.setShareRatio(100);
        ownerCollaborator.setContributedAmount(BigDecimal.ZERO);
        ownerCollaborator.setStatus(1);
        collaboratorMapper.insert(ownerCollaborator);

        operationLogger.record(userId, "COLLABORATION_CREATE", "collaborationId=" + collaboration.getId());
        log.info("User {} created collaboration {} for work {}", userId, collaboration.getId(), workId);
        return collaboration;
    }

    @Override
    @Transactional
    public void addCollaborator(Long userId, Long collaborationId, CollaboratorDTO dto) {
        Collaboration collaboration = requireOwnedCollaboration(userId, collaborationId);
        if (dto.getShareRatio() == null || dto.getShareRatio() <= 0 || dto.getShareRatio() > 100) {
            throw new BusinessException("Share ratio must be between 1 and 100");
        }
        if (dto.getUserId().equals(collaboration.getOwnerId())) {
            throw new BusinessException("The owner is already a collaborator");
        }

        QueryWrapper<Collaborator> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", collaborationId).eq("user_id", dto.getUserId());
        Collaborator existing = collaboratorMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException("This user is already a collaborator");
        }

        Collaborator collaborator = new Collaborator();
        collaborator.setCollaborationId(collaborationId);
        collaborator.setUserId(dto.getUserId());
        collaborator.setShareRatio(dto.getShareRatio());
        collaborator.setContributedAmount(BigDecimal.ZERO);
        collaborator.setStatus(1);
        collaboratorMapper.insert(collaborator);

        operationLogger.record(userId, "COLLABORATOR_ADD",
                "collaborationId=" + collaborationId + " collaboratorUserId=" + dto.getUserId());
        log.info("User {} added collaborator {} to collaboration {}", userId, dto.getUserId(), collaborationId);
    }

    @Override
    public List<Collaborator> listCollaborators(Long collaborationId) {
        QueryWrapper<Collaborator> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", collaborationId).orderByAsc("user_id");
        return collaboratorMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public LicenseOrder createLicenseOrder(Long userId, Long collaborationId, String buyerName, BigDecimal amount) {
        Collaboration collaboration = requireOwnedCollaboration(userId, collaborationId);
        if (buyerName == null || buyerName.trim().isEmpty()) {
            throw new BusinessException("Buyer name is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Order amount must be greater than zero");
        }

        LicenseOrder order = new LicenseOrder();
        order.setOrderNo(generateOrderNo());
        order.setWorkId(collaboration.getWorkId());
        order.setCollaborationId(collaborationId);
        order.setBuyerName(buyerName.trim());
        order.setAmount(amount);
        order.setStatus(LicenseOrder.STATUS_CREATED);
        licenseOrderMapper.insert(order);

        operationLogger.record(userId, "LICENSE_ORDER_CREATE",
                "orderNo=" + order.getOrderNo() + " collaborationId=" + collaborationId + " amount=" + amount);
        log.info("User {} created license order {} for collaboration {}", userId, order.getOrderNo(), collaborationId);
        return order;
    }

    @Override
    @Transactional
    public LicenseOrder confirmLicenseOrder(Long userId, Long orderId) {
        LicenseOrder order = licenseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("License order not found");
        }
        requireOwnedCollaboration(userId, order.getCollaborationId());
        if (order.getStatus() == null || order.getStatus() != LicenseOrder.STATUS_CREATED) {
            throw new BusinessException("Only created orders can be confirmed as paid");
        }

        QueryWrapper<RevenueEvent> duplicate = new QueryWrapper<>();
        duplicate.eq("order_id", orderId);
        if (revenueEventMapper.selectCount(duplicate) > 0) {
            throw new BusinessException("This order has already generated a revenue event");
        }

        CollaborationAgreement agreement = agreementService.getEffective(order.getCollaborationId());
        validateAgreementAgainstCurrentCollaborators(order.getCollaborationId(), agreement);

        RevenueEvent event = new RevenueEvent();
        event.setOrderId(orderId);
        event.setCollaborationId(order.getCollaborationId());
        event.setWorkId(order.getWorkId());
        event.setAmount(order.getAmount());
        event.setStatus(RevenueEvent.STATUS_AVAILABLE);
        event.setAgreementId(agreement.getId());
        event.setAgreementHash(agreement.getAgreementHash());
        event.setAgreementVersion(agreement.getVersion());
        event.setEventHash(buildRevenueEventHash(event));
        revenueEventMapper.insert(event);

        order.setStatus(LicenseOrder.STATUS_PAID);
        licenseOrderMapper.updateById(order);

        Collaboration collaboration = baseMapper.selectById(order.getCollaborationId());
        collaboration.setTotalRevenue(collaboration.getTotalRevenue().add(order.getAmount()));
        baseMapper.updateById(collaboration);

        operationLogger.record(userId, "LICENSE_ORDER_PAID",
                "orderNo=" + order.getOrderNo() + " revenueEventId=" + event.getId());
        operationLogger.record(userId, "REVENUE_EVENT_CREATED",
                "orderId=" + orderId + " amount=" + order.getAmount());
        businessAnchorService.enqueue(
                ChainAnchorRecord.TYPE_REVENUE_EVENT,
                event.getId(),
                event.getEventHash(),
                "collaboration/" + order.getCollaborationId() + "/revenue-event/" + event.getId());
        log.info("User {} confirmed license order {} paid", userId, order.getOrderNo());
        return order;
    }

    @Override
    public List<LicenseOrder> listLicenseOrders(Long collaborationId) {
        QueryWrapper<LicenseOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", collaborationId).orderByDesc("id");
        return licenseOrderMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void distributeRevenue(Long userId, Long collaborationId) {
        Collaboration collaboration = requireOwnedCollaboration(userId, collaborationId);

        QueryWrapper<RevenueEvent> eventWrapper = new QueryWrapper<>();
        eventWrapper.eq("collaboration_id", collaborationId)
                .eq("status", RevenueEvent.STATUS_AVAILABLE)
                .orderByAsc("id");
        List<RevenueEvent> events = revenueEventMapper.selectList(eventWrapper);
        if (events == null || events.isEmpty()) {
            throw new BusinessException("There is no available revenue event to settle");
        }

        BigDecimal settledTotal = BigDecimal.ZERO;
        Set<Long> settledAgreementIds = new LinkedHashSet<>();
        for (RevenueEvent event : events) {
            if (event.getAgreementId() == null
                    || event.getAgreementHash() == null
                    || event.getAgreementVersion() == null) {
                throw new BusinessException(
                        "Revenue event " + event.getId() + " has no agreement snapshot and cannot be settled");
            }

            CollaborationAgreement agreement = agreementService.getAgreement(event.getAgreementId());
            if (agreement == null || !event.getAgreementHash().equals(agreement.getAgreementHash())) {
                throw new BusinessException(
                        "Revenue event " + event.getId() + " agreement snapshot was tampered with");
            }
            settledAgreementIds.add(agreement.getId());

            List<AgreementItemDTO> items = agreementService.readItems(agreement);
            int totalRatio = items.stream().mapToInt(AgreementItemDTO::getShareRatio).sum();
            if (totalRatio != 100) {
                throw new BusinessException("Agreement ratios must add up to 100%");
            }

            // Claim the event atomically. A second settlement run cannot process it again.
            int claimed = revenueEventMapper.update(null, new LambdaUpdateWrapper<RevenueEvent>()
                    .eq(RevenueEvent::getId, event.getId())
                    .eq(RevenueEvent::getStatus, RevenueEvent.STATUS_AVAILABLE)
                    .set(RevenueEvent::getStatus, RevenueEvent.STATUS_SETTLED));
            if (claimed == 0) {
                throw new BusinessException("Revenue event " + event.getId() + " was already settled");
            }

            BigDecimal allocated = BigDecimal.ZERO;
            List<Map<String, Object>> allocations = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                AgreementItemDTO item = items.get(index);

                BigDecimal share;
                if (index == items.size() - 1) {
                    share = event.getAmount().subtract(allocated);
                } else {
                    share = event.getAmount().multiply(BigDecimal.valueOf(item.getShareRatio()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(share);
                }
                if (share.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("Settlement allocation produced a negative amount");
                }

                ProfitRecord record = new ProfitRecord();
                record.setCollaborationId(collaborationId);
                record.setUserId(item.getUserId());
                record.setAgreementId(agreement.getId());
                record.setOrderId(event.getOrderId());
                record.setRevenueEventId(event.getId());
                record.setAmount(share);
                record.setBlockchainTxHash(null);
                record.setChainStatus(0);
                record.setChainMode(blockchainService.mode());
                record.setDescription("Order " + event.getOrderId() + " settled under agreement v"
                        + agreement.getVersion() + "; chain settlement pending");
                profitRecordMapper.insert(record);

                Map<String, Object> allocation = new LinkedHashMap<>();
                allocation.put("userId", String.valueOf(item.getUserId()));
                allocation.put("shareRatio", String.valueOf(item.getShareRatio()));
                allocation.put("amount", share.setScale(2, RoundingMode.HALF_UP).toPlainString());
                allocations.add(allocation);

                for (Collaborator collaborator : listCollaborators(collaborationId)) {
                    if (collaborator.getUserId().equals(item.getUserId())) {
                        BigDecimal contributed = collaborator.getContributedAmount() == null
                                ? BigDecimal.ZERO : collaborator.getContributedAmount();
                        collaborator.setContributedAmount(contributed.add(share));
                        collaboratorMapper.updateById(collaborator);
                        break;
                    }
                }
            }

            LicenseOrder order = licenseOrderMapper.selectById(event.getOrderId());
            if (order != null) {
                order.setStatus(LicenseOrder.STATUS_SETTLED);
                licenseOrderMapper.updateById(order);
            }

            SettlementReceipt receipt = buildSettlementReceipt(event, agreement, allocations);
            settlementReceiptMapper.insert(receipt);
            ChainAnchorRecord anchor = businessAnchorService.enqueue(
                    ChainAnchorRecord.TYPE_SETTLEMENT_RECEIPT,
                    receipt.getId(),
                    receipt.getReceiptHash(),
                    "collaboration/" + collaborationId + "/settlement/" + receipt.getId());
            receipt.setChainStatus(anchor.getChainStatus());
            receipt.setChainMode(anchor.getChainMode());
            receipt.setBlockchainTxHash(anchor.getBlockchainTxHash());
            receipt.setBlockNumber(anchor.getBlockNumber());
            settlementReceiptMapper.updateById(receipt);

            settledTotal = settledTotal.add(event.getAmount());
        }

        BigDecimal previousDistributed = collaboration.getDistributedAmount() == null
                ? BigDecimal.ZERO : collaboration.getDistributedAmount();
        collaboration.setDistributedAmount(previousDistributed.add(settledTotal));
        baseMapper.updateById(collaboration);

        operationLogger.record(userId, "SETTLE_RUN",
                "collaborationId=" + collaborationId + " settledAmount=" + settledTotal
                        + " events=" + events.size());
        log.info("User {} settled {} for collaboration {} under agreements {}",
                userId, settledTotal, collaborationId, settledAgreementIds);
    }

    @Override
    public List<SettlementReceipt> listSettlementReceipts(Long collaborationId) {
        QueryWrapper<SettlementReceipt> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", collaborationId).orderByDesc("id");
        return settlementReceiptMapper.selectList(wrapper);
    }

    @Override
    public List<RevenueEvent> listRevenueEvents(Long collaborationId) {
        QueryWrapper<RevenueEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", collaborationId).orderByDesc("id");
        return revenueEventMapper.selectList(wrapper);
    }

    @Override
    public void requireParticipant(Long userId, Long collaborationId) {
        Collaboration collaboration = baseMapper.selectById(collaborationId);
        if (collaboration == null) {
            throw new BusinessException("Collaboration not found");
        }
        if (collaboration.getOwnerId().equals(userId)) {
            return;
        }
        Long participantCount = collaboratorMapper.selectCount(
                new QueryWrapper<Collaborator>()
                        .eq("collaboration_id", collaborationId)
                        .eq("user_id", userId)
                        .eq("status", 1));
        if (participantCount == null || participantCount == 0) {
            throw new BusinessException(403, "You are not a participant in this collaboration");
        }
    }

    private void validateAgreementAgainstCurrentCollaborators(
            Long collaborationId, CollaborationAgreement agreement) {
        if (agreement == null) {
            throw new BusinessException(
                    "An effective agreement confirmed by all collaborators is required before confirming revenue");
        }

        List<AgreementItemDTO> items = agreementService.readItems(agreement);
        int totalRatio = items.stream().mapToInt(AgreementItemDTO::getShareRatio).sum();
        if (totalRatio != 100) {
            throw new BusinessException("Effective agreement ratios must add up to 100%");
        }

        Set<Long> agreementUserIds = items.stream()
                .map(AgreementItemDTO::getUserId)
                .collect(Collectors.toSet());
        Set<Long> activeCollaboratorIds = listCollaborators(collaborationId).stream()
                .filter(item -> item.getStatus() != null && item.getStatus() == 1)
                .map(Collaborator::getUserId)
                .collect(Collectors.toSet());
        if (!agreementUserIds.equals(activeCollaboratorIds)) {
            throw new BusinessException(
                    "The effective agreement does not cover the current collaborator list; confirm a new agreement");
        }
    }

    private String buildRevenueEventHash(RevenueEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", "aic-revenue-event/v1");
        payload.put("orderId", String.valueOf(event.getOrderId()));
        payload.put("collaborationId", String.valueOf(event.getCollaborationId()));
        payload.put("workId", String.valueOf(event.getWorkId()));
        payload.put("amount", event.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        payload.put("agreementId", String.valueOf(event.getAgreementId()));
        payload.put("agreementHash", event.getAgreementHash());
        payload.put("agreementVersion", String.valueOf(event.getAgreementVersion()));
        return EvidenceHasher.hash(EvidenceHasher.canonicalJson(payload));
    }

    private SettlementReceipt buildSettlementReceipt(
            RevenueEvent event,
            CollaborationAgreement agreement,
            List<Map<String, Object>> allocations) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema", "aic-settlement-receipt/v1");
        payload.put("collaborationId", String.valueOf(event.getCollaborationId()));
        payload.put("revenueEventId", String.valueOf(event.getId()));
        payload.put("orderId", String.valueOf(event.getOrderId()));
        payload.put("agreementId", String.valueOf(agreement.getId()));
        payload.put("agreementHash", agreement.getAgreementHash());
        payload.put("agreementVersion", String.valueOf(agreement.getVersion()));
        payload.put("totalAmount", event.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        payload.put("allocations", allocations);

        String canonicalPayload = EvidenceHasher.canonicalJson(payload);
        String receiptHash = EvidenceHasher.hash(canonicalPayload);

        SettlementReceipt receipt = new SettlementReceipt();
        receipt.setReceiptNo("SR-" + event.getId() + "-" + receiptHash.substring(0, 16).toUpperCase());
        receipt.setCollaborationId(event.getCollaborationId());
        receipt.setRevenueEventId(event.getId());
        receipt.setOrderId(event.getOrderId());
        receipt.setAgreementId(agreement.getId());
        receipt.setAgreementHash(agreement.getAgreementHash());
        receipt.setAgreementVersion(agreement.getVersion());
        receipt.setTotalAmount(event.getAmount());
        receipt.setItemCount(allocations.size());
        receipt.setReceiptHash(receiptHash);
        receipt.setCanonicalPayload(canonicalPayload);
        receipt.setChainStatus(SettlementReceipt.CHAIN_PENDING);
        receipt.setChainMode(blockchainService.mode());
        return receipt;
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "ORD" + timestamp + random;
    }

    private Collaboration requireOwnedCollaboration(Long userId, Long collaborationId) {
        Collaboration collaboration = baseMapper.selectById(collaborationId);
        if (collaboration == null) {
            throw new BusinessException("Collaboration not found");
        }
        if (!collaboration.getOwnerId().equals(userId)) {
            throw new BusinessException("Only the collaboration owner can perform this action");
        }
        return collaboration;
    }
}
