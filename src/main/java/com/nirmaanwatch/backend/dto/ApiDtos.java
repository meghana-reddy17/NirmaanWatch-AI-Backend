package com.nirmaanwatch.backend.dto;

import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ReportCategory;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.entity.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserDto(Long id, String username, String email, String fullName, Role role, boolean enabled,
                          boolean mustChangePassword, Instant lastLoginAt) {
    }

    public record ProjectDto(Long id, String projectCode, String name, String description, String department,
                             String location, BigDecimal approvedBudget, int progressPercent, ProjectStatus status,
                             boolean publicVisible, LocalDate startDate, LocalDate expectedCompletionDate,
                             LocalDate actualCompletionDate, Instant createdAt, Instant updatedAt,
                             List<AssignmentDto> assignments) {
    }

    public record AssignmentDto(Long userId, String username, String fullName, Instant assignedAt) {
    }

    public record ProgressUpdateDto(Long id, int progressPercent, ProjectStatus status, String description,
                                    String milestone, LocalDate updateDate, Instant createdAt, String officerName) {
    }

    public record PublicReportDto(String publicReference, ReportCategory category, String subject, String description,
                                  ReportStatus status, Instant createdAt, int evidenceCount) {
    }

    public record ReportDto(Long id, String publicReference, Long projectId, String projectCode, String projectName,
                            ReportCategory category, String subject, String description, String issueLocation,
                            ReportStatus status, boolean anonymous, Instant createdAt, Instant updatedAt,
                            List<EvidenceMetaDto> evidence, List<ReportResponseDto> responses) {
    }

    public record ReportCreatedDto(String publicReference, String trackingToken, String message) {
    }

    public record EvidenceMetaDto(Long id, String originalFilename, String contentType, long sizeBytes,
                                  boolean publicVisible, Instant createdAt) {
    }

    public record ReportResponseDto(Long id, String authorName, Role authorRole, String message, Instant createdAt) {
    }

    public record AuditLogDto(Long id, String actorUsername, String action, String entityType, String entityId,
                              String details, Instant createdAt) {
    }

    public record NotificationDto(Long id, String title, String message, boolean read, Instant createdAt) {
    }

    public record RiskAssessmentDto(String method, String riskCategory, int score, List<String> reasons,
                                    List<String> recommendedActions, Instant assessedAt, String disclaimer) {
    }

    public record EvidenceAnalysisDto(String status, String message) {
    }

    public record PublicStatsDto(long totalPublicProjects, long inProgress, long completed, long requiringReview,
                                 boolean demonstrationData) {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 32) String projectCode,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String description,
            @NotBlank @Size(max = 120) String department,
            @NotBlank @Size(max = 200) String location,
            @NotNull @DecimalMin("0.01") BigDecimal approvedBudget,
            @Min(0) @Max(100) int progressPercent,
            @NotNull ProjectStatus status,
            boolean publicVisible,
            @NotNull LocalDate startDate,
            @NotNull LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank String description,
            @NotBlank @Size(max = 120) String department,
            @NotBlank @Size(max = 200) String location,
            @NotNull @DecimalMin("0.01") BigDecimal approvedBudget,
            @Min(0) @Max(100) int progressPercent,
            @NotNull ProjectStatus status,
            boolean publicVisible,
            @NotNull LocalDate startDate,
            @NotNull LocalDate expectedCompletionDate,
            LocalDate actualCompletionDate
    ) {
    }

    public record ProgressUpdateRequest(
            @Min(0) @Max(100) int progressPercent,
            @NotNull ProjectStatus status,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 200) String milestone,
            @NotNull LocalDate updateDate,
            LocalDate actualCompletionDate
    ) {
    }

    public record CreateReportRequest(
            @NotNull Long projectId,
            @NotNull ReportCategory category,
            @NotBlank @Size(max = 180) String subject,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 200) String issueLocation,
            boolean anonymous,
            boolean consentAcknowledged
    ) {
    }

    public record ReportStatusRequest(@NotNull ReportStatus status) {
    }

    public record ReportResponseRequest(@NotBlank @Size(max = 4000) String message) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role
    ) {
    }

    public record UserStatusRequest(@NotNull Boolean enabled) {
    }

    public record AssignmentRequest(@NotNull Long userId) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
