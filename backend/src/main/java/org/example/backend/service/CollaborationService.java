package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.dto.CollaboratorDTO;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.LicenseOrder;
import org.example.backend.entity.RevenueEvent;
import org.example.backend.entity.SettlementReceipt;
import java.math.BigDecimal;
import java.util.List;

public interface CollaborationService extends IService<Collaboration> {
    Collaboration createCollaboration(Long userId, Long workId);
    void addCollaborator(Long userId, Long collaborationId, CollaboratorDTO dto);
    List<Collaborator> listCollaborators(Long collaborationId);
    LicenseOrder createLicenseOrder(Long userId, Long collaborationId, String buyerName, BigDecimal amount);
    LicenseOrder confirmLicenseOrder(Long userId, Long orderId);
    List<LicenseOrder> listLicenseOrders(Long collaborationId);
    void distributeRevenue(Long userId, Long collaborationId);
    List<SettlementReceipt> listSettlementReceipts(Long collaborationId);
    List<RevenueEvent> listRevenueEvents(Long collaborationId);
    void requireParticipant(Long userId, Long collaborationId);
}
