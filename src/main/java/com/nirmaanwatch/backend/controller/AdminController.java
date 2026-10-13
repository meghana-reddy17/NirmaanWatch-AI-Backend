package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.repository.AuditLogRepository;
import com.nirmaanwatch.backend.repository.CitizenReportRepository;
import com.nirmaanwatch.backend.repository.ProjectRepository;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.DtoMapper;
import com.nirmaanwatch.backend.service.ProjectService;
import com.nirmaanwatch.backend.service.ReportService;
import com.nirmaanwatch.backend.service.RiskAssessmentService;
import com.nirmaanwatch.backend.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserAdminService userAdminService;
    private final CurrentUserService currentUserService;
    private final UserAccountRepository users;
    private final ProjectRepository projects;
    private final CitizenReportRepository reports;
    private final AuditLogRepository auditLogs;
    private final ProjectService projectService;
    private final ReportService reportService;
    private final RiskAssessmentService riskAssessmentService;

    public AdminController(UserAdminService userAdminService, CurrentUserService currentUserService,
                           UserAccountRepository users, ProjectRepository projects, CitizenReportRepository reports,
                           AuditLogRepository auditLogs, ProjectService projectService, ReportService reportService,
                           RiskAssessmentService riskAssessmentService) {
        this.userAdminService = userAdminService;
        this.currentUserService = currentUserService;
        this.users = users;
        this.projects = projects;
        this.reports = reports;
        this.auditLogs = auditLogs;
        this.projectService = projectService;
        this.reportService = reportService;
        this.riskAssessmentService = riskAssessmentService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        long total = projects.count();
        long active = projects.countByPublicVisibleTrueAndStatus(ProjectStatus.IN_PROGRESS) + countStatus(ProjectStatus.DELAYED);
        long completed = countStatus(ProjectStatus.COMPLETED);
        long openReports = reports.countByStatusNotIn(List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED, ReportStatus.CLOSED));
        long risk = projects.findAll().stream()
                .filter(p -> riskAssessmentService.assess(p).score() >= 70)
                .count();
        return Map.of(
                "totalProjects", total,
                "activeProjects", active,
                "completedProjects", completed,
                "openReports", openReports,
                "registeredUsers", users.count(),
                "projectsAtPotentialRisk", risk
        );
    }

    @GetMapping("/users")
    public ApiDtos.PageResponse<ApiDtos.UserDto> users(@RequestParam(required = false) String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "12") int size) {
        Page<UserAccount> result = userAdminService.search(q, page, size);
        return new ApiDtos.PageResponse<>(result.getContent().stream().map(DtoMapper::toUser).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.UserDto createUser(@Valid @RequestBody ApiDtos.CreateUserRequest request) {
        return DtoMapper.toUser(userAdminService.create(request, currentUserService.requireCurrent()));
    }

    @PutMapping("/users/{id}")
    public ApiDtos.UserDto updateUser(@PathVariable Long id, @Valid @RequestBody ApiDtos.UpdateUserRequest request) {
        return DtoMapper.toUser(userAdminService.update(id, request, currentUserService.requireCurrent()));
    }

    @PatchMapping("/users/{id}/status")
    public ApiDtos.UserDto status(@PathVariable Long id, @Valid @RequestBody ApiDtos.UserStatusRequest request) {
        return DtoMapper.toUser(userAdminService.setEnabled(id, request.enabled(), currentUserService.requireCurrent()));
    }

    @GetMapping("/projects")
    public ApiDtos.PageResponse<ApiDtos.ProjectDto> adminProjects(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) String department,
                                                                  @RequestParam(required = false) ProjectStatus status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "12") int size) {
        Page<Project> result = projectService.searchAll(q, department, status, page, size);
        List<ApiDtos.ProjectDto> content = result.getContent().stream()
                .map(p -> DtoMapper.toProject(p, projectService.assignmentsFor(p.getId())))
                .toList();
        return new ApiDtos.PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/reports")
    public ApiDtos.PageResponse<ApiDtos.ReportDto> adminReports(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "12") int size) {
        Page<com.nirmaanwatch.backend.entity.CitizenReport> result = reportService.allReports(page, size);
        List<ApiDtos.ReportDto> content = result.getContent().stream()
                .map(r -> DtoMapper.toReport(r, reportService.evidenceFor(r.getId()), reportService.responsesFor(r.getId())))
                .toList();
        return new ApiDtos.PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/audit-logs")
    public ApiDtos.PageResponse<ApiDtos.AuditLogDto> auditLogs(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        var result = auditLogs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 50)));
        return new ApiDtos.PageResponse<>(result.getContent().stream().map(DtoMapper::toAudit).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/risk-estimates")
    public List<Map<String, Object>> riskEstimates() {
        return projects.findAll().stream()
                .map(p -> Map.<String, Object>of("project", DtoMapper.toProject(p, List.of()), "risk", riskAssessmentService.assess(p)))
                .toList();
    }

    private long countStatus(ProjectStatus status) {
        return projects.findAll().stream().filter(p -> p.getStatus() == status).count();
    }
}
