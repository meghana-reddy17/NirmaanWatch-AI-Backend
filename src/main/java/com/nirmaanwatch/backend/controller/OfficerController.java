package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.repository.AppNotificationRepository;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.DtoMapper;
import com.nirmaanwatch.backend.service.ProjectService;
import com.nirmaanwatch.backend.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class OfficerController {

    private final CurrentUserService currentUserService;
    private final ProjectService projectService;
    private final ReportService reportService;
    private final AppNotificationRepository notifications;

    public OfficerController(CurrentUserService currentUserService, ProjectService projectService,
                             ReportService reportService, AppNotificationRepository notifications) {
        this.currentUserService = currentUserService;
        this.projectService = projectService;
        this.reportService = reportService;
        this.notifications = notifications;
    }

    @GetMapping("/api/officer/projects")
    public List<ApiDtos.ProjectDto> projects() {
        UserAccount user = staff();
        return projectService.assignedProjects(user).stream()
                .map(p -> DtoMapper.toProject(p, projectService.assignmentsFor(p.getId())))
                .toList();
    }

    @GetMapping("/api/officer/dashboard")
    public Map<String, Object> dashboard() {
        UserAccount user = staff();
        List<Project> assigned = projectService.assignedProjects(user);
        long inProgress = assigned.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).count();
        long completed = assigned.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count();
        long attention = assigned.stream().filter(p -> p.getStatus() == ProjectStatus.DELAYED || p.getStatus() == ProjectStatus.UNDER_REVIEW).count();
        List<Project> upcoming = assigned.stream()
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED)
                .sorted((a, b) -> a.getExpectedCompletionDate().compareTo(b.getExpectedCompletionDate()))
                .limit(5)
                .toList();
        Page<com.nirmaanwatch.backend.entity.CitizenReport> reports = reportService.officerReports(user, 0, 8);
        return Map.of(
                "totalAssigned", assigned.size(),
                "inProgress", inProgress,
                "completed", completed,
                "requiringAttention", attention,
                "upcomingDeadlines", upcoming.stream().map(p -> DtoMapper.toProject(p, List.of())).toList(),
                "recentUpdates", projectService.recentOfficerUpdates(user.getId()).stream().map(DtoMapper::toUpdate).toList(),
                "reportsRequiringReview", reports.getContent().stream()
                        .filter(r -> r.getStatus() == ReportStatus.SUBMITTED || r.getStatus() == ReportStatus.UNDER_REVIEW)
                        .map(r -> DtoMapper.toReport(r, List.of(), List.of()))
                        .toList()
        );
    }

    @GetMapping("/api/officer/reports")
    public ApiDtos.PageResponse<ApiDtos.ReportDto> reports(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "12") int size) {
        UserAccount user = staff();
        Page<com.nirmaanwatch.backend.entity.CitizenReport> result = reportService.officerReports(user, page, size);
        List<ApiDtos.ReportDto> content = result.getContent().stream()
                .map(r -> DtoMapper.toReport(r, reportService.evidenceFor(r.getId()), reportService.responsesFor(r.getId())))
                .toList();
        return new ApiDtos.PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/api/notifications")
    public List<ApiDtos.NotificationDto> myNotifications() {
        UserAccount user = currentUserService.requireCurrent();
        return notifications.findTop20ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(n -> new ApiDtos.NotificationDto(n.getId(), n.getTitle(), n.getMessage(), n.isReadFlag(), n.getCreatedAt()))
                .toList();
    }

    private UserAccount staff() {
        UserAccount user = currentUserService.requireCurrent();
        if (user.getRole() == Role.CITIZEN) {
            throw new com.nirmaanwatch.backend.exception.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Officer access is required.");
        }
        return user;
    }
}
