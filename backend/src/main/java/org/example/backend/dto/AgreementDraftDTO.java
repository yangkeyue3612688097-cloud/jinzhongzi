package org.example.backend.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class AgreementDraftDTO {
    @Valid
    @NotEmpty(message = "agreement items required")
    private List<AgreementItemDTO> items;

    private String negotiationNote;
}
