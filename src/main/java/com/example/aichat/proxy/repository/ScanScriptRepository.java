package com.example.aichat.proxy.repository;

import com.example.aichat.proxy.model.entity.ScanScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanScriptRepository extends JpaRepository<ScanScript, Long> {

    List<ScanScript> findByStatus(Integer status);
}
