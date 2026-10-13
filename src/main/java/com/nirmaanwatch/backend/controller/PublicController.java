package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.config.AppProperties;
import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.repository.CitizenReportRepository;
import com.nirmaanwatch.backend.repository.ProjectRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ProjectRepository projects;
    private final CitizenReportRepository reports;
    private final AppProperties properties;

    public PublicController(ProjectRepository projects, CitizenReportRepository reports, AppProperties properties) {
        this.projects = projects;
        this.reports = reports;
        this.properties = properties;
    }

    @GetMapping("/stats")
    public ApiDtos.PublicStatsDto stats() {
        long total = projects.countByPublicVisibleTrue();
        long inProgress = projects.countByPublicVisibleTrueAndStatus(ProjectStatus.IN_PROGRESS);
        long completed = projects.countByPublicVisibleTrueAndStatus(ProjectStatus.COMPLETED);
        long review = projects.countByPublicVisibleTrueAndStatus(ProjectStatus.UNDER_REVIEW)
                + projects.countByPublicVisibleTrueAndStatus(ProjectStatus.DELAYED);
        return new ApiDtos.PublicStatsDto(total, inProgress, completed, review, properties.isSeedDemo());
    }

    @GetMapping("/departments")
    public List<String> departments() {
        return projects.findDistinctPublicDepartments();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of(
                "anonymousReportsEnabled", properties.isAnonymousReportsEnabled(),
                "demonstrationData", properties.isSeedDemo(),
                "aiProvider", properties.getAiProvider(),
                "applicationName", "NirmaanWatch AI",
                "tagline", "Track Public Projects. Build Public Trust."
        );
    }
}
