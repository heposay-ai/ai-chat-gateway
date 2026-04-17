package com.example.aichat.proxy.repository;

import com.example.aichat.proxy.model.entity.ProxyRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProxyRequestLogRepository extends JpaRepository<ProxyRequestLog, Long> {

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0) FROM ProxyRequestLog l WHERE l.createdAt >= :since")
    Long sumTotalTokensSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.estimatedCost), 0) FROM ProxyRequestLog l WHERE l.createdAt >= :since")
    java.math.BigDecimal sumEstimatedCostSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM ProxyRequestLog l WHERE l.createdAt >= :since")
    Long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', l.createdAt) as day, " +
           "COALESCE(SUM(l.totalTokens), 0) as tokens, " +
           "COALESCE(SUM(l.estimatedCost), 0) as cost, " +
           "COUNT(l) as requests " +
           "FROM ProxyRequestLog l " +
           "WHERE l.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', l.createdAt) " +
           "ORDER BY day")
    List<Object[]> getDailyTrend(@Param("since") LocalDateTime since);

    @Query("SELECT l.model, COUNT(l) as cnt, COALESCE(SUM(l.totalTokens), 0) as tokens " +
           "FROM ProxyRequestLog l " +
           "WHERE l.createdAt >= :since " +
           "GROUP BY l.model ORDER BY cnt DESC")
    List<Object[]> getModelDistribution(@Param("since") LocalDateTime since);

    @Query("SELECT l.accountId, l.accountName, COUNT(l) as cnt, " +
           "COALESCE(SUM(l.totalTokens), 0) as tokens, " +
           "COALESCE(SUM(l.estimatedCost), 0) as cost " +
           "FROM ProxyRequestLog l " +
           "WHERE l.createdAt >= :since " +
           "GROUP BY l.accountId, l.accountName ORDER BY cnt DESC")
    List<Object[]> getAccountRanking(@Param("since") LocalDateTime since);
}
