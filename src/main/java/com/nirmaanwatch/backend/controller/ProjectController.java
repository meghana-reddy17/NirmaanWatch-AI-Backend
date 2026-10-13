package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectAssignment;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.DtoMapper;
import com.nirmaanwatch.backend.service.ProjectService;
import com.nirmaanwatch.backend.service.ReportService;
import com.nirmaanwatch.backend.service.RiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUserService currentUserService;
    private final RiskAssessmentService riskAssessmentService;
    private final ReportService reportService;

    public ProjectController(ProjectService projectService, CurrentUserService currentUserService,
                             RiskAssessmentService riskAssessmentService, ReportService reportService) {
        this.projectService = projectService;
        this.currentUserService = currentUserService;
        this.riskAssessmentService = riskAssessmentService;
        this.reportService = reportService;
    }

    @GetMapping
    public ApiDtos.PageResponse<ApiDtos.ProjectDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minProgress,
            @RequestParam(required = false) Integer maxProgress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort
    ) {
        Page<Project> result = projectService.searchPublic(q, department, status, location, minProgress, maxProgress, from, to, page, size, sort);
        List<ApiDtos.ProjectDto> content = result.getContent().stream()
                .map(p -> DtoMapper.toProject(p, List.of()))
                .toList();
        return new ApiDtos.PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public ApiDtos.ProjectDto get(@PathVariable Long id) {
        UserAccount viewer = currentUserService.findCurrent().orElse(null);
        Project project = projectService.requireVisible(id, viewer);
        List<ProjectAssignment> assignments = (viewer != null && (viewer.getRole() == Role.ADMIN || viewer.getRole() == Role.OFFICER))
                ? projectService.assignmentsFor(id) : List.of();
        return DtoMapper.toProject(project, assignments);
    }

    @GetMapping("/{id}/updates")
    public List<ApiDtos.ProgressUpdateDto> updates(@PathVariable Long id) {
        UserAccount viewer = currentUserService.findCurrent().orElse(null);
        projectService.requireVisible(id, viewer);
        return projectService.history(id).stream().map(DtoMapper::toUpdate).toList();
    }

    @GetMapping("/{id}/risk")
    public ApiDtos.RiskAssessmentDto risk(@PathVariable Long id) {
        UserAccount viewer = currentUserService.findCurrent().orElse(null);
        Project project = projectService.requireVisible(id, viewer);
        return riskAssessmentService.assess(project);
    }

    @GetMapping("/{id}/public-reports")
    public List<ApiDtos.PublicReportDto> publicReports(@PathVariable Long id) {
        UserAccount viewer = currentUserService.findCurrent().orElse(null);
        projectService.requireVisible(id, viewer);
        return reportService.publicForProject(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ProjectDto create(@Valid @RequestBody ApiDtos.CreateProjectRequest request) {
        UserAccount actor = requireStaff();
        Project project = projectService.create(request, actor);
        return DtoMapper.toProject(project, projectService.assignmentsFor(project.getId()));
    }

    @PutMapping("/{id}")
    public ApiDtos.ProjectDto update(@PathVariable Long id, @Valid @RequestBody ApiDtos.UpdateProjectRequest request) {
        UserAccount actor = requireStaff();
        Project project = projectService.update(id, request, actor);
        return DtoMapper.toProject(project, projectService.assignmentsFor(id));
    }

    @PatchMapping("/{id}/progress")
    public ApiDtos.ProgressUpdateDto progress(@PathVariable Long id, @Valid @RequestBody ApiDtos.ProgressUpdateRequest request) {
        UserAccount actor = requireStaff();
        return DtoMapper.toUpdate(projectService.updateProgress(id, request, actor));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id, currentUserService.requireCurrent());
    }

    @GetMapping("/{id}/assignments")
    public List<ApiDtos.AssignmentDto> assignments(@PathVariable Long id) {
        UserAccount actor = requireStaff();
        projectService.requireManaged(id, actor.getRole() == Role.ADMIN ? actor : actor);
        return projectService.assignmentsFor(id).stream()
                .map(a -> new ApiDtos.AssignmentDto(a.getUser().getId(), a.getUser().getUsername(), a.getUser().getFullName(), a.getAssignedAt()))
                .toList();
    }

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AssignmentDto assign(@PathVariable Long id, @Valid @RequestBody ApiDtos.AssignmentRequest request) {
        UserAccount actor = currentUserService.requireCurrent();
        if (actor.getRole() != Role.ADMIN) {
            throw new com.nirmaanwatch.backend.exception.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Only administrators can assign officers.");
        }
        ProjectAssignment assignment = projectService.assign(id, request.userId(), actor);
        return new ApiDtos.AssignmentDto(assignment.getUser().getId(), assignment.getUser().getUsername(),
                assignment.getUser().getFullName(), assignment.getAssignedAt());
    }

    @DeleteMapping("/{id}/assignments/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable Long id, @PathVariable Long userId) {
        UserAccount actor = currentUserService.requireCurrent();
        if (actor.getRole() != Role.ADMIN) {
            throw new com.nirmaanwatch.backend.exception.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Only administrators can change assignments.");
        }
        projectService.unassign(id, userId, actor);
    }

    private UserAccount requireStaff() {
        UserAccount actor = currentUserService.requireCurrent();
        if (actor.getRole() == Role.CITIZEN) {
            throw new com.nirmaanwatch.backend.exception.ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Citizens cannot change project records.");
        }
        return actor;
    }
}
