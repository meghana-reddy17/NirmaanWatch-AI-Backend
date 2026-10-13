package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.AuditLog;
import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.EvidenceFile;
import com.nirmaanwatch.backend.entity.ProgressUpdate;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectAssignment;
import com.nirmaanwatch.backend.entity.ReportResponse;
import com.nirmaanwatch.backend.entity.UserAccount;

import java.util.List;

public final class DtoMapper {
    private DtoMapper() {
    }

    public static ApiDtos.UserDto toUser(UserAccount user) {
        return new ApiDtos.UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getRole(), user.isEnabled(), user.isMustChangePassword(), user.getLastLoginAt());
    }

    public static ApiDtos.ProjectDto toProject(Project project, List<ProjectAssignment> assignments) {
        List<ApiDtos.AssignmentDto> assigned = assignments.stream()
                .map(a -> new ApiDtos.AssignmentDto(a.getUser().getId(), a.getUser().getUsername(),
                        a.getUser().getFullName(), a.getAssignedAt()))
                .toList();
        return new ApiDtos.ProjectDto(project.getId(), project.getProjectCode(), project.getName(), project.getDescription(),
                project.getDepartment(), project.getLocation(), project.getApprovedBudget(), project.getProgressPercent(),
                project.getStatus(), project.isPublicVisible(), project.getStartDate(), project.getExpectedCompletionDate(),
                project.getActualCompletionDate(), project.getCreatedAt(), project.getUpdatedAt(), assigned);
    }

    public static ApiDtos.ProgressUpdateDto toUpdate(ProgressUpdate update) {
        return new ApiDtos.ProgressUpdateDto(update.getId(), update.getProgressPercent(), update.getStatus(),
                update.getDescription(), update.getMilestone(), update.getUpdateDate(), update.getCreatedAt(),
                update.getOfficer().getFullName());
    }

    public static ApiDtos.EvidenceMetaDto toEvidence(EvidenceFile file) {
        return new ApiDtos.EvidenceMetaDto(file.getId(), file.getOriginalFilename(), file.getContentType(),
                file.getSizeBytes(), file.isPublicVisible(), file.getCreatedAt());
    }

    public static ApiDtos.ReportResponseDto toResponse(ReportResponse response) {
        return new ApiDtos.ReportResponseDto(response.getId(), response.getAuthor().getFullName(),
                response.getAuthor().getRole(), response.getMessage(), response.getCreatedAt());
    }

    public static ApiDtos.PublicReportDto toPublicReport(CitizenReport report, int evidenceCount) {
        return new ApiDtos.PublicReportDto(report.getPublicReference(), report.getCategory(), report.getSubject(),
                report.getDescription(), report.getStatus(), report.getCreatedAt(), evidenceCount);
    }

    public static ApiDtos.ReportDto toReport(CitizenReport report, List<EvidenceFile> evidence, List<ReportResponse> responses) {
        return new ApiDtos.ReportDto(report.getId(), report.getPublicReference(), report.getProject().getId(),
                report.getProject().getProjectCode(), report.getProject().getName(), report.getCategory(),
                report.getSubject(), report.getDescription(), report.getIssueLocation(), report.getStatus(),
                report.isAnonymous(), report.getCreatedAt(), report.getUpdatedAt(),
                evidence.stream().map(DtoMapper::toEvidence).toList(),
                responses.stream().map(DtoMapper::toResponse).toList());
    }

    public static ApiDtos.AuditLogDto toAudit(AuditLog log) {
        return new ApiDtos.AuditLogDto(log.getId(), log.getActor() == null ? null : log.getActor().getUsername(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(), log.getCreatedAt());
    }
}
