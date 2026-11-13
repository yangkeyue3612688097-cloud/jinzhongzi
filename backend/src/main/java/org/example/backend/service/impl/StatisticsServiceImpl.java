package org.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.AiWork;
import org.example.backend.entity.Collaboration;
import org.example.backend.entity.Collaborator;
import org.example.backend.entity.Evidence;
import org.example.backend.mapper.AiWorkMapper;
import org.example.backend.mapper.CollaborationMapper;
import org.example.backend.mapper.CollaboratorMapper;
import org.example.backend.mapper.EvidenceMapper;
import org.example.backend.mapper.ProfitRecordMapper;
import org.example.backend.entity.ProfitRecord;
import org.example.backend.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private AiWorkMapper aiWorkMapper;

    @Autowired
    private CollaborationMapper collaborationMapper;

    @Autowired
    private CollaboratorMapper collaboratorMapper;

    @Autowired
    private EvidenceMapper evidenceMapper;

    @Autowired
    private ProfitRecordMapper profitRecordMapper;

    @Override
    public Map<String, Object> getDashboardStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<AiWork> workWrapper = new QueryWrapper<>();
        workWrapper.eq("user_id", userId);
        long totalWorks = aiWorkMapper.selectCount(workWrapper);
        stats.put("totalWorks", totalWorks);

        QueryWrapper<Collaboration> ownerWrapper = new QueryWrapper<>();
        ownerWrapper.eq("owner_id", userId);
        long ownedCollabs = collaborationMapper.selectCount(ownerWrapper);

        QueryWrapper<Collaborator> collabWrapper = new QueryWrapper<>();
        collabWrapper.eq("user_id", userId);
        long participatedCollabs = collaboratorMapper.selectCount(collabWrapper);
        stats.put("totalCollaborations", ownedCollabs + participatedCollabs);

        QueryWrapper<Collaborator> myCollabWrapper = new QueryWrapper<>();
        myCollabWrapper.eq("user_id", userId);
        List<Collaborator> myCollabs = collaboratorMapper.selectList(myCollabWrapper);
        BigDecimal totalRevenue = myCollabs.stream()
                .map(Collaborator::getContributedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalRevenue", totalRevenue);

        stats.put("totalEvidence", countEvidence(userId));
        stats.put("revenueTrend", revenueTrend(userId));
        return stats;
    }

    private long countEvidence(Long userId) {
        List<Object> workIds = aiWorkMapper.selectObjs(new QueryWrapper<AiWork>()
                .select("id")
                .eq("user_id", userId));
        if (workIds == null || workIds.isEmpty()) {
            return 0L;
        }
        return evidenceMapper.selectCount(new QueryWrapper<Evidence>().in("work_id", workIds));
    }

    /**
     * 近 7 天收益趋势，按收益流水的 create_time 汇总。
     */
    private List<Map<String, Object>> revenueTrend(Long userId) {
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> daily = new HashMap<>();
        try {
            List<ProfitRecord> records = profitRecordMapper.selectList(
                    new QueryWrapper<ProfitRecord>()
                            .eq("user_id", userId)
                            .ge("create_time", today.minusDays(6).atStartOfDay()));
            for (ProfitRecord record : records) {
                if (record.getCreateTime() == null) {
                    continue;
                }
                String key = record.getCreateTime().format(DateTimeFormatter.ofPattern("MM-dd"));
                BigDecimal amount = record.getAmount() == null ? BigDecimal.ZERO : record.getAmount();
                daily.merge(key, amount, BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("Load revenue trend failed for user {}", userId, e);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> dayData = new HashMap<>();
            String key = date.format(DateTimeFormatter.ofPattern("MM-dd"));
            dayData.put("date", key);
            dayData.put("revenue", daily.getOrDefault(key, BigDecimal.ZERO));
            trend.add(dayData);
        }
        return trend;
    }
}
