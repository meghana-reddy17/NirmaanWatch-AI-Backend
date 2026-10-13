package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.ReportResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportResponseRepository extends JpaRepository<ReportResponse, Long> {
    List<ReportResponse> findByReportIdOrderByCreatedAtAsc(Long reportId);
}
