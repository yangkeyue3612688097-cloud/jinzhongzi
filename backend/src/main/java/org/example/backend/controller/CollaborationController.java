package org.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.Result;
import org.example.backend.dto.CollaboratorDTO;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.LicenseOrder;
import org.example.backend.entity.RevenueEvent;
import org.example.backend.entity.SettlementReceipt;
import org.example.backend.entity.User;
import org.example.backend.mapper.CollaboratorMapper;
import org.example.backend.service.CollaborationService;
import org.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/collaboration")
public class CollaborationController {

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private UserService userService;

    @Autowired
    private CollaboratorMapper collaboratorMapper;

    @PostMapping("/create")
    public Result<Collaboration> createCollaboration(HttpServletRequest request, @RequestParam Long workId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(collaborationService.createCollaboration(userId, workId));
    }

    @PostMapping("/add-collaborator")
    public Result<Void> addCollaborator(
            HttpServletRequest request,
            @RequestParam Long collaborationId,
            @RequestBody CollaboratorDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.addCollaborator(userId, collaborationId, dto);
        return Result.success();
    }

    @GetMapping("/collaborators/{collaborationId}")
    public Result<List<Map<String, Object>>> listCollaborators(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        List<Collaborator> collaborators = collaborationService.listCollaborators(collaborationId);
        List<Map<String, Object>> result = collaborators.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("userId", c.getUserId());
            map.put("shareRatio", c.getShareRatio());
            map.put("contributedAmount", c.getContributedAmount());
            map.put("status", c.getStatus());

            User user = userService.getById(c.getUserId());
            map.put("userName", user == null ? "未知用户" : user.getUsername());
            map.put("email", user == null ? "" : user.getEmail());
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping("/order/create")
    public Result<LicenseOrder> createLicenseOrder(
            HttpServletRequest request,
            @RequestParam Long collaborationId,
            @RequestParam String buyerName,
            @RequestParam BigDecimal amount) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(collaborationService.createLicenseOrder(userId, collaborationId, buyerName, amount));
    }

    @PostMapping("/order/confirm/{orderId}")
    public Result<LicenseOrder> confirmLicenseOrder(HttpServletRequest request, @PathVariable Long orderId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(collaborationService.confirmLicenseOrder(userId, orderId));
    }

    @GetMapping("/orders/{collaborationId}")
    public Result<List<LicenseOrder>> listLicenseOrders(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(collaborationService.listLicenseOrders(collaborationId));
    }

    @GetMapping("/revenue-events/{collaborationId}")
    public Result<List<RevenueEvent>> listRevenueEvents(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(collaborationService.listRevenueEvents(collaborationId));
    }

    @GetMapping("/receipts/{collaborationId}")
    public Result<List<SettlementReceipt>> listSettlementReceipts(
            HttpServletRequest request,
            @PathVariable Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, collaborationId);
        return Result.success(collaborationService.listSettlementReceipts(collaborationId));
    }

    @PostMapping("/distribute")
    public Result<Void> distributeRevenue(HttpServletRequest request, @RequestParam Long collaborationId) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.distributeRevenue(userId, collaborationId);
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    public Result<Collaboration> getCollaborationDetail(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        collaborationService.requireParticipant(userId, id);
        return Result.success(collaborationService.getById(id));
    }

    @GetMapping("/my")
    public Result<List<Map<String, Object>>> getMyCollaborations(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        QueryWrapper<Collaboration> ownerWrapper = new QueryWrapper<>();
        ownerWrapper.eq("owner_id", userId).orderByDesc("id");
        List<Collaboration> ownedCollabs = collaborationService.list(ownerWrapper);

        QueryWrapper<Collaborator> collabWrapper = new QueryWrapper<>();
        collabWrapper.eq("user_id", userId);
        List<Collaborator> collaboratorRecords = collaboratorMapper.selectList(collabWrapper);
        List<Long> collabIds = collaboratorRecords.stream()
                .map(Collaborator::getCollaborationId)
                .collect(Collectors.toList());

        List<Collaboration> participatingCollabs = new ArrayList<>();
        if (!collabIds.isEmpty()) {
            participatingCollabs = collaborationService.listByIds(collabIds);
        }

        Map<Long, Collaboration> allCollabs = new HashMap<>();
        ownedCollabs.forEach(c -> allCollabs.put(c.getId(), c));
        participatingCollabs.forEach(c -> allCollabs.put(c.getId(), c));

        List<Map<String, Object>> result = allCollabs.values().stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("workId", c.getWorkId());
            map.put("ownerId", c.getOwnerId());
            map.put("totalRevenue", c.getTotalRevenue());
            map.put("distributedAmount", c.getDistributedAmount());
            map.put("status", c.getStatus());
            map.put("chainStatus", c.getChainStatus());
            map.put("chainMode", c.getChainMode());
            map.put("currentAgreementId", c.getCurrentAgreementId());
            map.put("role", c.getOwnerId().equals(userId) ? "所有者" : "协作者");

            User owner = userService.getById(c.getOwnerId());
            map.put("ownerName", owner != null ? owner.getUsername() : "未知");
            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }
}
