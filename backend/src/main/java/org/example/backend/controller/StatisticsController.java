package org.example.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.common.Result;
import org.example.backend.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboardStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> stats = statisticsService.getDashboardStats(userId);
        return Result.success(stats);
    }
}
