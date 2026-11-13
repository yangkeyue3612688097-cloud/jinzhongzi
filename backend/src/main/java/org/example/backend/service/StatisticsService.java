package org.example.backend.service;

import java.util.Map;

public interface StatisticsService {
    Map<String, Object> getDashboardStats(Long userId);
}
