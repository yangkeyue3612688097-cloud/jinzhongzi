package org.example.backend.chain;

import lombok.Data;

@Data
public class ChainAnchor {

    /** 是否已在链上落块 */
    private boolean anchored;
    private Boolean verified;
    private String txHash;
    private Long blockNumber;
    private String mode;
    private String message;

    public static ChainAnchor pending(String mode, String message) {
        ChainAnchor anchor = new ChainAnchor();
        anchor.setAnchored(false);
        anchor.setMode(mode);
        anchor.setMessage(message);
        return anchor;
    }

    public static ChainAnchor anchored(String mode, String txHash, Long blockNumber) {
        ChainAnchor anchor = new ChainAnchor();
        anchor.setAnchored(true);
        anchor.setVerified(true);
        anchor.setMode(mode);
        anchor.setTxHash(txHash);
        anchor.setBlockNumber(blockNumber);
        anchor.setMessage("已上链");
        return anchor;
    }

    public static ChainAnchor verified(String mode, boolean verified, String message) {
        ChainAnchor anchor = new ChainAnchor();
        anchor.setAnchored(false);
        anchor.setVerified(verified);
        anchor.setMode(mode);
        anchor.setMessage(message);
        return anchor;
    }

    public static ChainAnchor failed(String mode, String message) {
        ChainAnchor anchor = new ChainAnchor();
        anchor.setAnchored(false);
        anchor.setMode(mode);
        anchor.setMessage(message);
        return anchor;
    }
}
