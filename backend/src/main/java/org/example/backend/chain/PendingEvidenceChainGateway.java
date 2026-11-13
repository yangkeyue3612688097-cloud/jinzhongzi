package org.example.backend.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 合约未接入时的默认实现：只记录本地证据，不伪造交易哈希。
 */
@Component
@ConditionalOnProperty(name = "app.chain.mode", havingValue = "pending", matchIfMissing = true)
public class PendingEvidenceChainGateway implements EvidenceChainGateway {

    private static final Logger log = LoggerFactory.getLogger(PendingEvidenceChainGateway.class);

    @Override
    public String mode() {
        return "pending";
    }

    @Override
    public ChainAnchor anchor(ChainAnchorRequest request) {
        log.info("Chain gateway in pending mode, anchor kept locally: businessId={}, hash={}, type={}",
                request.getBusinessId(), request.getDataHash(), request.getBusinessType());
        return ChainAnchor.pending(mode(), "合约尚未接入，证据已本地留存，待上链");
    }

    @Override
    public ChainAnchor query(String dataHash) {
        return ChainAnchor.pending(mode(), "合约尚未接入，无法查询链上状态");
    }
}
