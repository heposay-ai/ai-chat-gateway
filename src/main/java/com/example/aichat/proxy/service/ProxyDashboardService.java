package com.example.aichat.proxy.service;

import com.example.aichat.proxy.repository.ProxyAccountRepository;
import com.example.aichat.proxy.repository.ProxyRequestLogRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class ProxyDashboardService {

    private final ProxyAccountRepository accountRepository;
    private final ProxyRequestLogRepository logRepository;

    public ProxyDashboardService(ProxyAccountRepository accountRepository,
                                  ProxyRequestLogRepository logRepository) {
        this.accountRepository = accountRepository;
        this.logRepository = logRepository;
    }

    public Map<String, Object> getOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return Map.of(
                "totalAccounts", accountRepository.count(),
                "healthyAccounts", accountRepository.countByHealthStatus("healthy"),
                "totalRequests", logRepository.countSince(todayStart),
                "totalTokens", logRepository.sumTotalTokensSince(todayStart)
        );
    }

    public List<Map<String, Object>> getTokenTrend(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<Object[]> raw = logRepository.getDailyTrend(since);
        return buildTrendData(raw, days, "tokens");
    }

    public List<Map<String, Object>> getCostTrend(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<Object[]> raw = logRepository.getDailyTrend(since);
        return buildTrendData(raw, days, "cost");
    }

    public List<Map<String, Object>> getModelDistribution(int days) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<Object[]> raw = logRepository.getModelDistribution(since);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            result.add(Map.of(
                    "model", row[0] != null ? row[0].toString() : "unknown",
                    "count", ((Number) row[1]).longValue(),
                    "tokens", ((Number) row[2]).longValue()
            ));
        }
        return result;
    }

    public List<Map<String, Object>> getAccountRanking(int days, int limit) {
        LocalDateTime since = LocalDate.now().minusDays(days).atStartOfDay();
        List<Object[]> raw = logRepository.getAccountRanking(since);
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Object[] row : raw) {
            if (count >= limit) break;
            result.add(Map.of(
                    "accountId", row[0] != null ? ((Number) row[0]).longValue() : 0,
                    "name", row[1] != null ? row[1].toString() : "unknown",
                    "requests", ((Number) row[2]).longValue(),
                    "tokens", ((Number) row[3]).longValue(),
                    "cost", row[4] != null ? ((Number) row[4]).doubleValue() : 0
            ));
            count++;
        }
        return result;
    }

    private List<Map<String, Object>> buildTrendData(List<Object[]> raw, int days, String valueKey) {
        // 建立日期映射
        Map<String, Object[]> dateMap = new LinkedHashMap<>();
        for (Object[] row : raw) {
            String date = row[0] != null ? row[0].toString() : "";
            dateMap.put(date, row);
        }

        // 补齐缺失日期
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).toString();
            Object[] data = dateMap.get(date);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date);
            if (data != null) {
                item.put("tokens", ((Number) data[1]).longValue());
                item.put("cost", data[2] instanceof BigDecimal ? ((BigDecimal) data[2]).doubleValue() : ((Number) data[2]).doubleValue());
                item.put("requests", ((Number) data[3]).longValue());
            } else {
                item.put("tokens", 0L);
                item.put("cost", 0.0);
                item.put("requests", 0L);
            }
            result.add(item);
        }
        return result;
    }
}
