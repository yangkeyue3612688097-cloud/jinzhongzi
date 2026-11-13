package org.example.backend.chain;

/**
 * 链上存证的唯一出口。
 *
 * <p>合约接入时只需提供一个新的实现类，并在 application.yml 里把
 * app.chain.mode 切过去，业务代码不需要改动。
 */
public interface EvidenceChainGateway {

    String mode();

    /** 写入存证，返回交易结果 */
    ChainAnchor anchor(ChainAnchorRequest request);

    /** 查询链上是否存在该存证 */
    ChainAnchor query(String dataHash);

    default ChainAnchor verify(ChainAnchorRequest request) {
        return ChainAnchor.pending(mode(), "The current chain mode cannot verify anchors");
    }

    default String operatorAddress() {
        return null;
    }
}
