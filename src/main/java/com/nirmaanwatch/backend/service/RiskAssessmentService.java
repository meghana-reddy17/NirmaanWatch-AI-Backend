package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.ProgressUpdate;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.repository.CitizenReportRepository;
import com.nirmaanwatch.backend.repository.ProgressUpdateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAssessmentService {

    private final ProgressUpdateRepository updates;
    private final CitizenReportRepository reports;

    public RiskAssessmentService(ProgressUpdateRepository updates, CitizenReportRepository reports) {
        this.updates = updates;
        this.reports = reports;
    }

    public ApiDtos.RiskAssessmentDto assess(Project project) {
        List<String> reasons = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        int score = 10;

        LocalDate today = LocalDate.now();
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(project.getStartDate(), project.getExpectedCompletionDate()));
        long elapsedDays = Math.max(0, ChronoUnit.DAYS.between(project.getStartDate(), today));
        double elapsedRatio = Math.min(1.5, elapsedDays / (double) totalDays);
        double expectedProgress = elapsedRatio * 100.0;
        int gap = (int) Math.round(expectedProgress - project.getProgressPercent());

        if (project.getStatus() == ProjectStatus.DELAYED) {
            score += 25;
            reasons.add("The recorded project status is DELAYED.");
            actions.add("Publish a recovery plan and a revised milestone date.");
        }
        if (project.getExpectedCompletionDate().isBefore(today) && project.getStatus() != ProjectStatus.COMPLETED) {
            score += 30;
            reasons.add("The expected completion date is in the past and the project is not marked completed.");
            actions.add("Confirm actual completion or formally revise the schedule.");
        }
        if (gap > 20) {
            score += 20;
            reasons.add("Elapsed schedule time is ahead of recorded physical progress by about " + gap + " percentage points.");
            actions.add("Investigate blockers and post a progress update with evidence of current work.");
        } else if (gap > 8) {
            score += 10;
            reasons.add("Progress is moderately behind a linear time-based estimate.");
        }
        if (project.getStatus() == ProjectStatus.ON_HOLD) {
            score += 15;
            reasons.add("The project is currently on hold.");
        }

        ProgressUpdate latest = updates.findTopByProjectIdOrderByCreatedAtDesc(project.getId()).orElse(null);
        if (latest == null) {
            score += 15;
            reasons.add("No progress update history has been published yet.");
            actions.add("Officers should publish the first official update.");
        } else if (latest.getCreatedAt().isBefore(Instant.now().minus(30, ChronoUnit.DAYS))
                && project.getStatus() != ProjectStatus.COMPLETED) {
            score += 15;
            reasons.add("The latest progress update is more than 30 days old.");
            actions.add("Publish a current status update.");
        }

        long openReports = reports.countByProjectIdAndStatusNotIn(project.getId(),
                List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED, ReportStatus.CLOSED));
        if (openReports > 0) {
            score += Math.min(20, (int) openReports * 5);
            reasons.add(openReports + " citizen report(s) remain open for this project.");
            actions.add("Review citizen reports and publish an authorized response.");
            List<CitizenReport> blockers = reports.findByProjectIdAndStatusNot(project.getId(), ReportStatus.CLOSED);
            boolean safety = blockers.stream().anyMatch(r -> r.getCategory().name().equals("SAFETY") || r.getCategory().name().equals("DELAY"));
            if (safety) {
                score += 5;
                reasons.add("Open reports include delay or safety categories.");
            }
        }

        score = Math.min(100, score);
        String category;
        if (score >= 70) {
            category = "HIGH";
        } else if (score >= 40) {
            category = "MEDIUM";
        } else {
            category = "LOW";
        }
        if (reasons.isEmpty()) {
            reasons.add("Available schedule, progress, and report indicators do not currently suggest elevated delay risk.");
        }
        if (actions.isEmpty()) {
            actions.add("Continue publishing regular progress updates.");
        }

        return new ApiDtos.RiskAssessmentDto(
                "RULES_BASED_ESTIMATE",
                category,
                score,
                reasons,
                actions,
                Instant.now(),
                "This is a rules-based estimate using published dates, progress, update recency, and open reports. It is not a verified finding and must not be treated as a fraud conclusion."
        );
    }
}
