package org.example.backend.service;

import org.example.backend.dto.AgreementDraftDTO;
import org.example.backend.dto.AgreementItemDTO;
import org.example.backend.dto.CollaborationAgreementVO;
import org.example.backend.entity.CollaborationAgreement;

import java.util.List;

public interface CollaborationAgreementService {
    CollaborationAgreementVO createDraft(Long userId, Long collaborationId, AgreementDraftDTO dto);

    CollaborationAgreementVO confirm(Long userId, Long agreementId);

    CollaborationAgreementVO getDetail(Long agreementId);

    CollaborationAgreementVO getCurrent(Long collaborationId);

    CollaborationAgreementVO getEffectiveVO(Long collaborationId);

    List<CollaborationAgreementVO> history(Long collaborationId);

    CollaborationAgreement getEffective(Long collaborationId);

    CollaborationAgreement getAgreement(Long agreementId);

    List<AgreementItemDTO> readItems(CollaborationAgreement agreement);
}
