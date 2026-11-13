package org.example.backend.service;

import org.example.backend.chain.ChainAnchor;
import org.example.backend.chain.ChainAnchorRequest;
import org.example.backend.chain.EvidenceChainGateway;
import org.example.backend.config.FiscoBcosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链上能力的门面。具体实现由 {@link EvidenceChainGateway} 提供，
 * 业务层不关心底层是待接入状态还是 FISCO BCOS。
 */
@Service
public class BlockchainService {

    @Autowired
    private EvidenceChainGateway gateway;

    @Autowired
    private FiscoBcosConfig fiscoConfig;

    public String mode() {
        return gateway.mode();
    }

    public ChainAnchor anchor(ChainAnchorRequest request) {
        return gateway.anchor(request);
    }

    public ChainAnchor query(String dataHash) {
        return gateway.query(dataHash);
    }

    public ChainAnchor verify(ChainAnchorRequest request) {
        return gateway.verify(request);
    }

    public String operatorAddress() {
        try {
            return gateway.operatorAddress();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isContractConfigured() {
        String address = fiscoConfig.getContract() == null ? null : fiscoConfig.getContract().getEvidenceAddress();
        return address != null && !address.trim().isEmpty();
    }

    public boolean isBusinessAnchorConfigured() {
        String address = fiscoConfig.getContract() == null
                ? null : fiscoConfig.getContract().getBusinessAnchorAddress();
        return address != null && !address.trim().isEmpty();
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", mode());
        status.put("activeContract", "business_anchor");
        status.put("contractConfigured", isContractConfigured());
        status.put("businessAnchorConfigured", isBusinessAnchorConfigured());
        status.put("sdkConfigured", hasText(fiscoConfig.getSdkConfigPath()));
        status.put("signerConfigured", hasText(fiscoConfig.getPrivateKey()));
        status.put("abiConfigured", hasText(fiscoConfig.getBusinessAnchorAbi()));
        status.put("operatorAddress", operatorAddress());
        status.put("chainEnabled", "fisco".equalsIgnoreCase(mode()));
        status.put("note", "pending".equals(mode())
                ? "存证与验证在本地完成，上链接口已预留"
                : "已切换到链上实现");
        return status;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
