package com.example.aichat.proxy.repository;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProxyAccountRepository extends JpaRepository<ProxyAccount, Long> {

    List<ProxyAccount> findByStatusAndHealthStatus(Integer status, String healthStatus);

    @Query("SELECT a FROM ProxyAccount a WHERE " +
           "(:healthStatus IS NULL OR a.healthStatus = :healthStatus) AND " +
           "(:keyword IS NULL OR a.name LIKE %:keyword% OR a.endpointUrl LIKE %:keyword% OR a.provider LIKE %:keyword%)")
    Page<ProxyAccount> findByFilters(@Param("healthStatus") String healthStatus,
                                      @Param("keyword") String keyword,
                                      Pageable pageable);

    long countByHealthStatus(String healthStatus);

    long countByStatus(Integer status);

    List<ProxyAccount> findByStatus(Integer status);
}
