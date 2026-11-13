package org.example.backend.chain;

import org.example.backend.config.FiscoBcosConfig;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.transaction.model.dto.CallResponse;
import org.fisco.bcos.sdk.transaction.model.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * FISCO BCOS adapter for the BusinessAnchor contract.
 *
 * <p>The adapter only reports success after the SDK returns a valid receipt
 * with a successful status and a transaction hash.</p>
 */
@Component
@ConditionalOnProperty(name = "app.chain.mode", havingValue = "fisco")
public class FiscoEvidenceChainGateway implements EvidenceChainGateway, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FiscoEvidenceChainGateway.class);

    @Autowired
    private FiscoBcosConfig config;

    private volatile Resources resources;

    @Override
    public String mode() {
        return "fisco";
    }

    @Override
    public ChainAnchor anchor(ChainAnchorRequest request) {
        try {
            Resources current = resources();
            List<Object> params = new ArrayList<>();
            params.add(ChainEncoding.businessIdToBytes32(request.getBusinessId()));
            params.add(ChainEncoding.sha256ToBytes32(request.getDataHash()));
            params.add(ChainEncoding.businessTypeCode(request.getBusinessType()));
            params.add(request.getMetadataUri());

            TransactionResponse response = current.processor.sendTransactionAndGetResponse(
                    current.contractAddress,
                    current.abi,
                    "anchor",
                    params);

            if (response == null) {
                return ChainAnchor.failed(mode(), "FISCO SDK returned no transaction response");
            }
            if (response.getReturnCode() != 0) {
                return ChainAnchor.failed(mode(), responseMessage(response.getReturnMessage()));
            }

            TransactionReceipt receipt = response.getTransactionReceipt();
            if (receipt == null) {
                return ChainAnchor.failed(mode(), "FISCO transaction receipt is missing");
            }
            if (!receipt.isStatusOK()) {
                return ChainAnchor.failed(mode(), receiptMessage(receipt));
            }

            String txHash = receipt.getTransactionHash();
            if (isBlank(txHash)) {
                return ChainAnchor.failed(mode(), "FISCO receipt contains no transaction hash");
            }
            Long blockNumber = parseBlockNumber(receipt.getBlockNumber());
            return ChainAnchor.anchored(mode(), txHash, blockNumber);
        } catch (Exception e) {
            log.warn("FISCO anchor failed: type={}, id={}",
                    request.getBusinessType(), request.getBusinessId(), e);
            return ChainAnchor.failed(mode(), truncate(e.getMessage(), 1000));
        }
    }

    @Override
    public ChainAnchor query(String dataHash) {
        return ChainAnchor.failed(mode(), "Business id and type are required for FISCO verification");
    }

    @Override
    public ChainAnchor verify(ChainAnchorRequest request) {
        try {
            Resources current = resources();
            List<Object> params = new ArrayList<>();
            params.add(ChainEncoding.businessIdToBytes32(request.getBusinessId()));
            params.add(ChainEncoding.sha256ToBytes32(request.getDataHash()));
            params.add(ChainEncoding.businessTypeCode(request.getBusinessType()));

            CallResponse response = current.processor.sendCall(
                    current.keyPair.getAddress(),
                    current.contractAddress,
                    current.abi,
                    "verify",
                    params);
            if (response == null) {
                return ChainAnchor.failed(mode(), "FISCO SDK returned no verification response");
            }
            if (response.getReturnCode() != 0) {
                return ChainAnchor.failed(mode(), responseMessage(response.getReturnMessage()));
            }
            if (response.getReturnObject() == null || response.getReturnObject().isEmpty()) {
                return ChainAnchor.failed(mode(), "FISCO verification returned no value");
            }

            Object value = response.getReturnObject().get(0);
            if (!(value instanceof Boolean)) {
                return ChainAnchor.failed(mode(), "FISCO verification returned a non-boolean value");
            }
            boolean verified = (Boolean) value;
            return ChainAnchor.verified(
                    mode(),
                    verified,
                    verified ? "Anchor verified on chain" : "No matching anchor exists on chain");
        } catch (Exception e) {
            log.warn("FISCO verification failed: type={}, id={}",
                    request.getBusinessType(), request.getBusinessId(), e);
            return ChainAnchor.failed(mode(), truncate(e.getMessage(), 1000));
        }
    }

    @Override
    public String operatorAddress() {
        try {
            return resources().keyPair.getAddress();
        } catch (Exception e) {
            log.debug("FISCO operator address is unavailable", e);
            return null;
        }
    }

    private Resources resources() throws Exception {
        Resources current = resources;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (resources == null) {
                resources = initialize();
            }
            return resources;
        }
    }

    private Resources initialize() throws Exception {
        String sdkConfigPath = requireConfig(config.getSdkConfigPath(), "FISCO SDK config path");
        String privateKey = requireConfig(config.getPrivateKey(), "FISCO signer private key");
        String contractAddress = requireConfig(
                config.getContract() == null ? null : config.getContract().getBusinessAnchorAddress(),
                "BusinessAnchor contract address");
        String abi = loadAbi(requireConfig(config.getBusinessAnchorAbi(), "BusinessAnchor ABI path"));

        BcosSDK sdk = null;
        try {
            sdk = BcosSDK.build(sdkConfigPath);
            Client client = sdk.getClient(config.getGroupId());
            if (client == null) {
                throw new IllegalStateException("FISCO group " + config.getGroupId() + " is unavailable");
            }
            CryptoKeyPair keyPair = client.getCryptoSuite().createKeyPair(privateKey);
            AssembleTransactionProcessor processor =
                    TransactionProcessorFactory.createAssembleTransactionProcessor(
                            client, keyPair, abi, "");
            log.info("FISCO BusinessAnchor gateway initialized, operator={}", keyPair.getAddress());
            return new Resources(sdk, keyPair, processor, contractAddress, abi);
        } catch (Exception e) {
            if (sdk != null) {
                sdk.stopAll();
            }
            throw e;
        }
    }

    private String loadAbi(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String resourceName = location.substring("classpath:".length());
            InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (input == null) {
                throw new IOException("ABI resource not found: " + location);
            }
            try {
                return readString(input);
            } finally {
                input.close();
            }
        }

        Path path = Paths.get(location);
        if (!Files.isRegularFile(path)) {
            throw new IOException("ABI file not found: " + location);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private String readString(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder result = new StringBuilder();
        int count;
        while ((count = input.read(buffer)) >= 0) {
            result.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private String requireConfig(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalStateException(name + " is not configured");
        }
        return value.trim();
    }

    private Long parseBlockNumber(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            String normalized = value.trim();
            return normalized.startsWith("0x")
                    ? Long.parseLong(normalized.substring(2), 16)
                    : Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String responseMessage(String value) {
        return isBlank(value) ? "FISCO SDK call failed" : truncate(value, 1000);
    }

    private String receiptMessage(TransactionReceipt receipt) {
        String status = isBlank(receipt.getStatus()) ? "unknown" : receipt.getStatus();
        String message = isBlank(receipt.getStatusMsg())
                ? receipt.getMessage() : receipt.getStatusMsg();
        return "FISCO transaction failed, status=" + status
                + (isBlank(message) ? "" : ", message=" + message);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public void destroy() {
        Resources current;
        synchronized (this) {
            current = resources;
            resources = null;
        }
        if (current != null) {
            current.sdk.stopAll();
        }
    }

    private static final class Resources {
        private final BcosSDK sdk;
        private final CryptoKeyPair keyPair;
        private final AssembleTransactionProcessor processor;
        private final String contractAddress;
        private final String abi;

        private Resources(BcosSDK sdk,
                          CryptoKeyPair keyPair,
                          AssembleTransactionProcessor processor,
                          String contractAddress,
                          String abi) {
            this.sdk = sdk;
            this.keyPair = keyPair;
            this.processor = processor;
            this.contractAddress = contractAddress;
            this.abi = abi;
        }
    }
}
