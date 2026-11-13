package org.example.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String avatar;
    private String blockchainAddress;
    private String publicKey;
    private String identitySignature;
    private LocalDateTime identityAttestedAt;
    private boolean identityAttested;
    private boolean walletManaged = true;
}
