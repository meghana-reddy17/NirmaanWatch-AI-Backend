package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CitizenReportRepository extends JpaRepository<CitizenReport, Long> {
    Optional<CitizenReport> findByPublicReferenceAndTrackingToken(String publicReference, String trackingToken);
    boolean existsByPublicReference(String publicReference);
    List<CitizenReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
    Page<CitizenReport> findByProjectIdIn(List<Long> projectIds, Pageable pageable);
    long countByStatusNotIn(List<ReportStatus> statuses);
    long countByProjectIdAndStatusNotIn(Long projectId, List<ReportStatus> statuses);
    List<CitizenReport> findByProjectIdAndStatusNot(Long projectId, ReportStatus status);
}
