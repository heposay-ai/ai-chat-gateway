package com.example.aichat.proxy.repository;

import com.example.aichat.proxy.model.entity.ScanTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {

    Page<ScanTask> findByScriptId(Long scriptId, Pageable pageable);

    Page<ScanTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ScanTask> findByStatus(String status, Pageable pageable);
}
