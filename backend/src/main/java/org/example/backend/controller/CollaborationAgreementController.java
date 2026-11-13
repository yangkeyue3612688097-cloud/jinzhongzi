package org.example.backend.controller;

import org.example.backend.common.Result;
import org.example.backend.dto.AgreementDraftDTO;
import org.example.backend.dto.CollaborationAgreementVO;
import org.example.backend.entity.CollaborationAgreement;
import org.example.backend.service.CollaborationAgreementService;
import org.example.backend.service.CollaborationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/collaboration/agreement")
public class CollaborationAgreementController {

    @Autowired
    private CollaborationAgreementService agreementService;

    @Autowired
    private CollaborationService collaborationService;

    @PostMapping("/draft")
    public Result<CollaborationAgreementVO> createDraft(
            HttpServletRequest request,
            @RequestParam Long collaborationId,
            @Valid @RequestBody AgreementDraftDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(agreementService.createDraft(userId, collaborationId, dto));
    }

    @PostMapping("/{agreementId}/confirm")
    public Result<CollaborationAgreementVO> confirm(
            HttpServletRequest request,
            @PathVariable Long agreementId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(agreementService.confirm(userId, agreementId));
    }

    @GetMapping("/{agreementId}")
    public Result<CollaborationAgreementVO> detail(
            HttpServletRequest request,
            @PathVariable Long agreementId) {
        Long userId = (Long) request.getAttribute("userId");
        CollaborationAgreement agreement = agreementService.getAgreement(agreementId);
        if (agreement == null) {
            return Result.error("Agreement not found");
        }
        collaborationService.requireParticipant(userId, agreement.getCollaborationId());
        return Result.success(agreementService.getDetail(agreementId));
    }

    @GetMapping("/current/{collaborationId}")
    public Result<CollaborationAgreementVO> current(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(agreementService.getCurrent(collaborationId));
    }

    @GetMapping("/effective/{collaborationId}")
    public Result<CollaborationAgreementVO> effective(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(agreementService.getEffectiveVO(collaborationId));
    }

    @GetMapping("/history/{collaborationId}")
    public Result<List<CollaborationAgreementVO>> history(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(agreementService.history(collaborationId));
    }
}
