package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.BusinessException;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.CollaborationInvite;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.User;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.mapper.CollaborationInviteMapper;
import org.example.backend.mapper.CollaborationMapper;
import org.example.backend.mapper.CollaboratorMapper;
import org.example.backend.mapper.UserMapper;
import org.example.backend.service.BlockchainService;
import org.example.backend.service.CollaborationInviteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class CollaborationInviteServiceImpl extends ServiceImpl<CollaborationInviteMapper, CollaborationInvite> implements CollaborationInviteService {

    @Autowired
    private CollaborationMapper collaborationMapper;

    @Autowired
    private CollaboratorMapper collaboratorMapper;

    @Autowired
    private AiWorkMapper aiWorkMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BlockchainService blockchainService;

    @Override
    public CollaborationInvite sendInvite(Long inviterId, Long workId, Long inviteeId, String message) {
        AiWork work = aiWorkMapper.selectById(workId);
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        if (!work.getUserId().equals(inviterId)) {
            throw new BusinessException("Only the work owner can invite collaborators");
        }
        if (inviterId.equals(inviteeId)) {
            throw new BusinessException("You cannot invite yourself");
        }
        User invitee = userMapper.selectById(inviteeId);
        if (invitee == null) {
            throw new BusinessException("Invitee not found");
        }

        QueryWrapper<CollaborationInvite> wrapper = new QueryWrapper<>();
        wrapper.eq("work_id", workId).eq("inviter_id", inviterId).eq("invitee_id", inviteeId).eq("status", 0);
        CollaborationInvite existing = baseMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException("An invitation is already pending");
        }

        CollaborationInvite invite = new CollaborationInvite();
        invite.setWorkId(workId);
        invite.setInviterId(inviterId);
        invite.setInviteeId(inviteeId);
        invite.setMessage(message);
        invite.setStatus(0);
        baseMapper.insert(invite);
        log.info("User {} invited user {} to work {}", inviterId, inviteeId, workId);
        return invite;
    }

    @Override
    @Transactional
    public boolean acceptInvite(Long inviteeId, Long inviteId) {
        CollaborationInvite invite = baseMapper.selectById(inviteId);
        if (invite == null) {
            throw new BusinessException("Invitation not found");
        }
        if (!invite.getInviteeId().equals(inviteeId)) {
            throw new BusinessException("This invitation belongs to another user");
        }
        if (invite.getStatus() != 0) {
            throw new BusinessException("Invitation has already been handled");
        }

        AiWork work = aiWorkMapper.selectById(invite.getWorkId());
        if (work == null) {
            throw new BusinessException("Work not found");
        }
        if (!work.getUserId().equals(invite.getInviterId())) {
            throw new BusinessException("Inviter is not the work owner");
        }

        invite.setStatus(1);
        baseMapper.updateById(invite);

        QueryWrapper<Collaboration> collabWrapper = new QueryWrapper<>();
        collabWrapper.eq("work_id", invite.getWorkId())
                .eq("owner_id", invite.getInviterId())
                .orderByDesc("id")
                .last("LIMIT 1");
        Collaboration collaboration = collaborationMapper.selectOne(collabWrapper);

        if (collaboration == null) {
            collaboration = new Collaboration();
            collaboration.setWorkId(invite.getWorkId());
            collaboration.setOwnerId(invite.getInviterId());
            collaboration.setWorkHash(work.getFileHash());
            collaboration.setBlockchainTxHash(null);
            collaboration.setChainStatus(0);
            collaboration.setChainMode(blockchainService.mode());
            collaboration.setTotalRevenue(BigDecimal.ZERO);
            collaboration.setDistributedAmount(BigDecimal.ZERO);
            collaboration.setStatus(1);
            collaborationMapper.insert(collaboration);

            Collaborator owner = new Collaborator();
            owner.setCollaborationId(collaboration.getId());
            owner.setUserId(invite.getInviterId());
            owner.setShareRatio(100);
            owner.setContributedAmount(BigDecimal.ZERO);
            owner.setStatus(1);
            collaboratorMapper.insert(owner);
        }

        QueryWrapper<Collaborator> collabCheck = new QueryWrapper<>();
        collabCheck.eq("collaboration_id", collaboration.getId()).eq("user_id", inviteeId);
        Collaborator existingCollaborator = collaboratorMapper.selectOne(collabCheck);
        if (existingCollaborator == null) {
            Collaborator collaborator = new Collaborator();
            collaborator.setCollaborationId(collaboration.getId());
            collaborator.setUserId(inviteeId);
            collaborator.setShareRatio(0);
            collaborator.setContributedAmount(BigDecimal.ZERO);
            collaborator.setStatus(1);
            collaboratorMapper.insert(collaborator);
            log.info("User {} accepted the invitation and joined collaboration {}", inviteeId, collaboration.getId());
        }

        return true;
    }

    @Override
    public boolean rejectInvite(Long inviteeId, Long inviteId) {
        CollaborationInvite invite = baseMapper.selectById(inviteId);
        if (invite == null) {
            throw new BusinessException("Invitation not found");
        }
        if (!invite.getInviteeId().equals(inviteeId)) {
            throw new BusinessException("This invitation belongs to another user");
        }
        if (invite.getStatus() != 0) {
            throw new BusinessException("Invitation has already been handled");
        }
        invite.setStatus(2);
        return baseMapper.updateById(invite) > 0;
    }

    @Override
    public List<CollaborationInvite> getMyInvites(Long userId) {
        QueryWrapper<CollaborationInvite> wrapper = new QueryWrapper<>();
        wrapper.eq("invitee_id", userId).orderByDesc("create_time");
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<CollaborationInvite> getWorkInvites(Long workId) {
        QueryWrapper<CollaborationInvite> wrapper = new QueryWrapper<>();
        wrapper.eq("work_id", workId).orderByDesc("create_time");
        return baseMapper.selectList(wrapper);
    }
}
