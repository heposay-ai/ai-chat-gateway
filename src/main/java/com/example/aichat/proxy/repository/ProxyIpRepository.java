package com.example.aichat.proxy.repository;

import com.example.aichat.proxy.model.entity.ProxyIp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProxyIpRepository extends JpaRepository<ProxyIp, Long> {

    Optional<ProxyIp> findByIpAndPort(String ip, Integer port);

    Page<ProxyIp> findByStatus(String status, Pageable pageable);

    @Query("SELECT p FROM ProxyIp p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:keyword IS NULL OR p.ip LIKE %:keyword% OR p.source LIKE %:keyword% OR p.tags LIKE %:keyword%)")
    Page<ProxyIp> findByFilters(@Param("status") String status,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    List<ProxyIp> findByIdIn(List<Long> ids);

    long countByStatus(String status);
}
