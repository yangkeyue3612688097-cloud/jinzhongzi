package org.example.backend.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class AgreementItemDTO {
    @NotNull(message = "userId required")
    private Long userId;

    @NotNull(message = "shareRatio required")
    @Min(value = 0, message = "shareRatio cannot be negative")
    @Max(value = 100, message = "shareRatio cannot exceed 100")
    private Integer shareRatio;
}
