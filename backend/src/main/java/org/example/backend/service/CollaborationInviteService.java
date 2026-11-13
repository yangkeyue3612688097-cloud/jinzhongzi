package org.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.backend.entity.CollaborationInvite;
import java.util.List;

public interface CollaborationInviteService extends IService<CollaborationInvite> {
    CollaborationInvite sendInvite(Long inviterId, Long workId, Long inviteeId, String message);
    boolean acceptInvite(Long inviteeId, Long inviteId);
    boolean rejectInvite(Long inviteeId, Long inviteId);
    List<CollaborationInvite> getMyInvites(Long userId);
    List<CollaborationInvite> getWorkInvites(Long workId);
}
