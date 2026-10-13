package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.config.AppProperties;
import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.EvidenceFile;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectAssignment;
import com.nirmaanwatch.backend.entity.ReportResponse;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.CitizenReportRepository;
import com.nirmaanwatch.backend.repository.EvidenceFileRepository;
import com.nirmaanwatch.backend.repository.ProjectAssignmentRepository;
import com.nirmaanwatch.backend.repository.ReportResponseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReportService {

    private final CitizenReportRepository reports;
    private final EvidenceFileRepository evidence;
    private final ReportResponseRepository responses;
    private final ProjectService projectService;
    private final ProjectAssignmentRepository assignments;
    private final EvidenceService evidenceService;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final AppProperties properties;

    public ReportService(CitizenReportRepository reports, EvidenceFileRepository evidence,
                         ReportResponseRepository responses, ProjectService projectService,
                         ProjectAssignmentRepository assignments, EvidenceService evidenceService,
                         AuditService auditService, NotificationService notifications, AppProperties properties) {
        this.reports = reports;
        this.evidence = evidence;
        this.responses = responses;
        this.projectService = projectService;
        this.assignments = assignments;
        this.evidenceService = evidenceService;
        this.auditService = auditService;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Transactional
    public ApiDtos.ReportCreatedDto submit(ApiDtos.CreateReportRequest request, MultipartFile file, UserAccount reporter) {
        if (!request.consentAcknowledged()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You must acknowledge the privacy notice before submitting.");
        }
        boolean anonymous = request.anonymous();
        if (anonymous && !properties.isAnonymousReportsEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Anonymous reports are currently disabled. Please sign in.");
        }
        if (!anonymous && reporter == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in is required unless you submit an anonymous report.");
        }
        Project project = projectService.requireVisible(request.projectId(), reporter);
        CitizenReport report = new CitizenReport();
        report.setPublicReference(nextReference());
        report.setTrackingToken(UUID.randomUUID().toString());
        report.setProject(project);
        report.setReporter(anonymous ? null : reporter);
        report.setCategory(request.category());
        report.setSubject(request.subject().trim());
        report.setDescription(request.description().trim());
        report.setIssueLocation(request.issueLocation());
        report.setStatus(ReportStatus.SUBMITTED);
        report.setAnonymous(anonymous);
        report.setConsentAcknowledged(true);
        CitizenReport saved = reports.save(report);
        if (file != null && !file.isEmpty()) {
            evidenceService.store(saved, file, reporter);
        }
        assignments.findByProjectId(project.getId()).forEach(a ->
                notifications.notify(a.getUser(), "New citizen report",
                        "Report " + saved.getPublicReference() + " was filed for " + project.getProjectCode() + "."));
        auditService.record(reporter, "REPORT_SUBMITTED", "CitizenReport", saved.getId().toString(), saved.getPublicReference());
        return new ApiDtos.ReportCreatedDto(saved.getPublicReference(), saved.getTrackingToken(),
                "Report received. Store the reference number and tracking token to check status.");
    }

    public CitizenReport track(String reference, String token) {
        return reports.findByPublicReferenceAndTrackingToken(reference, token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No report matched that reference and token."));
    }

    public List<CitizenReport> mine(UserAccount user) {
        return reports.findByReporterIdOrderByCreatedAtDesc(user.getId());
    }

    public CitizenReport requireAccessible(Long id, UserAccount user) {
        CitizenReport report = reports.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report was not found."));
        if (user.getRole() == Role.ADMIN) {
            return report;
        }
        if (user.getRole() == Role.OFFICER && projectService.isAssigned(report.getProject().getId(), user.getId())) {
            return report;
        }
        if (report.getReporter() != null && report.getReporter().getId().equals(user.getId())) {
            return report;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You cannot view this report.");
    }

    public Page<CitizenReport> officerReports(UserAccount officer, int page, int size) {
        List<Long> ids = assignments.findByUserId(officer.getId()).stream().map(a -> a.getProject().getId()).toList();
        if (ids.isEmpty()) {
            return Page.empty();
        }
        return reports.findByProjectIdIn(ids, PageRequest.of(page, size));
    }

    public Page<CitizenReport> allReports(int page, int size) {
        return reports.findAll(PageRequest.of(page, Math.min(size, 50)));
    }

    @Transactional
    public CitizenReport updateStatus(Long id, ReportStatus status, UserAccount actor) {
        CitizenReport report = requireAccessible(id, actor);
        if (actor.getRole() == Role.CITIZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Citizens cannot change report status.");
        }
        report.setStatus(status);
        CitizenReport saved = reports.save(report);
        auditService.record(actor, "REPORT_STATUS_CHANGED", "CitizenReport", id.toString(), status.name());
        return saved;
    }

    @Transactional
    public ReportResponse addResponse(Long id, String message, UserAccount actor) {
        CitizenReport report = requireAccessible(id, actor);
        if (actor.getRole() == Role.CITIZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Citizens cannot post official responses.");
        }
        ReportResponse response = new ReportResponse();
        response.setReport(report);
        response.setAuthor(actor);
        response.setMessage(message.trim());
        ReportResponse saved = responses.save(response);
        if (report.getStatus() == ReportStatus.SUBMITTED) {
            report.setStatus(ReportStatus.UNDER_REVIEW);
            reports.save(report);
        }
        auditService.record(actor, "REPORT_RESPONSE_ADDED", "CitizenReport", id.toString(), null);
        return saved;
    }

    public List<EvidenceFile> evidenceFor(Long reportId) {
        return evidence.findByReportId(reportId);
    }

    public List<ReportResponse> responsesFor(Long reportId) {
        return responses.findByReportIdOrderByCreatedAtAsc(reportId);
    }

    public List<ApiDtos.PublicReportDto> publicForProject(Long projectId) {
        return reports.findByProjectIdAndStatusNot(projectId, ReportStatus.REJECTED).stream()
                .map(r -> DtoMapper.toPublicReport(r, (int) evidence.countByReportId(r.getId())))
                .toList();
    }

    private String nextReference() {
        String ref;
        do {
            ref = "NW-R-" + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt(), 8).toUpperCase();
        } while (reports.existsByPublicReference(ref));
        return ref;
    }
}
