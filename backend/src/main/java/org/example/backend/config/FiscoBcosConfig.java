package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fisco")
public class FiscoBcosConfig {

    private NodeConfig node = new NodeConfig();
    private int groupId = 1;
    private String sdkCertPath;
    private String sdkConfigPath;
    private String privateKey;
    private String businessAnchorAbi = "classpath:abi/BusinessAnchor.abi.json";
    private ContractConfig contract = new ContractConfig();

    @Data
    public static class NodeConfig {
        private String ip = "127.0.0.1";
        private int channelPort = 20200;
    }

    @Data
    public static class ContractConfig {
        private String evidenceAddress = "";
        private String collaborationAddress = "";
        private String businessAnchorAddress = "";
    }
}
