package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.EvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {
    List<EvidenceFile> findByReportId(Long reportId);
    long countByReportId(Long reportId);
}
