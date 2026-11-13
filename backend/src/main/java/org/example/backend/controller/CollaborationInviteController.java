package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.Result;
import org.example.backend.entity.CollaborationInvite;
import org.example.backend.service.CollaborationInviteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/collaboration/invite")
public class CollaborationInviteController {

    @Autowired
    private CollaborationInviteService collaborationInviteService;

    @PostMapping("/send")
    public Result<CollaborationInvite> sendInvite(
            HttpServletRequest request,
            @RequestParam Long workId,
            @RequestParam Long inviteeId,
            @RequestParam(required = false) String message) {
        Long inviterId = (Long) request.getAttribute("userId");
        return Result.success(collaborationInviteService.sendInvite(inviterId, workId, inviteeId, message));
    }

    @PostMapping("/accept/{inviteId}")
    public Result<Boolean> acceptInvite(HttpServletRequest request, @PathVariable Long inviteId) {
        Long inviteeId = (Long) request.getAttribute("userId");
        return Result.success(collaborationInviteService.acceptInvite(inviteeId, inviteId));
    }

    @PostMapping("/reject/{inviteId}")
    public Result<Boolean> rejectInvite(HttpServletRequest request, @PathVariable Long inviteId) {
        Long inviteeId = (Long) request.getAttribute("userId");
        return Result.success(collaborationInviteService.rejectInvite(inviteeId, inviteId));
    }

    @GetMapping("/my")
    public Result<List<CollaborationInvite>> getMyInvites(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(collaborationInviteService.getMyInvites(userId));
    }

    @GetMapping("/work/{workId}")
    public Result<List<CollaborationInvite>> getWorkInvites(@PathVariable Long workId) {
        return Result.success(collaborationInviteService.getWorkInvites(workId));
    }
}
