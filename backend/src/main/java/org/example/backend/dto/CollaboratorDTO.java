package org.example.backend.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class CollaboratorDTO {
    @NotNull(message = "userId required")
    private Long userId;
    private String userName;
    @NotNull(message = "shareRatio required")
    private Integer shareRatio;
}
