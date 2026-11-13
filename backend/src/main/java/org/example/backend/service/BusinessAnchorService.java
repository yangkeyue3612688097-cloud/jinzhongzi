package org.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.backend.chain.ChainAnchor;
import org.example.backend.chain.ChainAnchorRequest;
import org.example.backend.chain.ChainEncoding;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.mapper.ChainAnchorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Persists the business-to-chain handoff before a chain adapter is available.
 * In pending mode it deliberately leaves tx_hash null instead of fabricating one.
 */
@Service
public class BusinessAnchorService {

    private static final Logger log = LoggerFactory.getLogger(BusinessAnchorService.class);

    @Autowired
    private ChainAnchorMapper chainAnchorMapper;

    @Autowired
    private BlockchainService blockchainService;

    public ChainAnchorRecord enqueue(String businessType, Long businessId, String dataHash, String metadataUri) {
        if (businessType == null || businessId == null || dataHash == null || dataHash.isEmpty()) {
            throw new IllegalArgumentException("Business anchor requires type, id and data hash");
        }
        businessType = ChainEncoding.normalizeBusinessType(businessType);
        dataHash = ChainEncoding.normalizeSha256(dataHash);

        ChainAnchorRecord existing = chainAnchorMapper.selectOne(
                new LambdaQueryWrapper<ChainAnchorRecord>()
                        .eq(ChainAnchorRecord::getBusinessType, businessType)
                        .eq(ChainAnchorRecord::getBusinessId, businessId)
                        .eq(ChainAnchorRecord::getDataHash, dataHash)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        ChainAnchorRecord record = new ChainAnchorRecord();
        record.setBusinessType(businessType);
        record.setBusinessId(businessId);
        record.setDataHash(dataHash);
        record.setMetadataUri(metadataUri);
        record.setChainStatus(ChainAnchorRecord.STATUS_PENDING);
        record.setChainMode(blockchainService.mode());
        record.setRetryCount(0);
        chainAnchorMapper.insert(record);

        // The pending gateway must not produce a fake transaction hash.
        if (!"pending".equalsIgnoreCase(blockchainService.mode())) {
            try {
                ChainAnchor result = blockchainService.anchor(
                        new ChainAnchorRequest(businessId, dataHash, businessType, metadataUri));
                if (result != null && result.isAnchored()) {
                    record.setChainStatus(ChainAnchorRecord.STATUS_ANCHORED);
                    record.setBlockchainTxHash(result.getTxHash());
                    record.setBlockNumber(result.getBlockNumber());
                    record.setErrorMessage(null);
                } else {
                    record.setChainStatus(ChainAnchorRecord.STATUS_FAILED);
                    record.setErrorMessage(result == null ? "Chain gateway returned no result" : result.getMessage());
                }
                chainAnchorMapper.updateById(record);
            } catch (Exception e) {
                record.setChainStatus(ChainAnchorRecord.STATUS_FAILED);
                record.setErrorMessage(truncate(e.getMessage(), 1000));
                chainAnchorMapper.updateById(record);
                log.warn("Business anchor failed: type={}, id={}, hash={}",
                        businessType, businessId, dataHash, e);
            }
        }
        return record;
    }

    /**
     * Retries a pending or failed outbox record. An anchored record is
     * immutable and is returned as-is.
     */
    public ChainAnchorRecord retry(ChainAnchorRecord record) {
        if (record == null || record.getId() == null) {
            throw new IllegalArgumentException("A persisted business anchor is required");
        }

        ChainAnchorRecord current = chainAnchorMapper.selectById(record.getId());
        if (current == null) {
            throw new IllegalArgumentException("Business anchor not found: " + record.getId());
        }
        if (Integer.valueOf(ChainAnchorRecord.STATUS_ANCHORED).equals(current.getChainStatus())) {
            return current;
        }

        int currentRetryCount = current.getRetryCount() == null ? 0 : current.getRetryCount();
        current.setRetryCount(currentRetryCount + 1);
        current.setChainMode(blockchainService.mode());
        current.setErrorMessage(null);
        current.setBlockchainTxHash(null);
        current.setBlockNumber(null);

        if ("pending".equalsIgnoreCase(blockchainService.mode())) {
            current.setChainStatus(ChainAnchorRecord.STATUS_PENDING);
            current.setErrorMessage("Waiting for the FISCO gateway to be configured");
            chainAnchorMapper.updateById(current);
            return current;
        }

        try {
            ChainAnchor result = blockchainService.anchor(new ChainAnchorRequest(
                    current.getBusinessId(),
                    current.getDataHash(),
                    current.getBusinessType(),
                    current.getMetadataUri()));
            if (result != null && result.isAnchored()) {
                current.setChainStatus(ChainAnchorRecord.STATUS_ANCHORED);
                current.setBlockchainTxHash(result.getTxHash());
                current.setBlockNumber(result.getBlockNumber());
                current.setErrorMessage(null);
            } else {
                current.setChainStatus(ChainAnchorRecord.STATUS_FAILED);
                current.setErrorMessage(result == null
                        ? "Chain gateway returned no result"
                        : truncate(result.getMessage(), 1000));
            }
        } catch (Exception e) {
            current.setChainStatus(ChainAnchorRecord.STATUS_FAILED);
            current.setErrorMessage(truncate(e.getMessage(), 1000));
            log.warn("Business anchor retry failed: type={}, id={}, hash={}",
                    current.getBusinessType(), current.getBusinessId(), current.getDataHash(), e);
        }
        chainAnchorMapper.updateById(current);
        return current;
    }

    public List<ChainAnchorRecord> list(String businessType, Long businessId) {
        businessType = ChainEncoding.normalizeBusinessType(businessType);
        return chainAnchorMapper.selectList(
                new LambdaQueryWrapper<ChainAnchorRecord>()
                        .eq(ChainAnchorRecord::getBusinessType, businessType)
                        .eq(ChainAnchorRecord::getBusinessId, businessId)
                        .orderByDesc(ChainAnchorRecord::getId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
