package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectAssignment;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.ProgressUpdate;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.ProjectAssignmentRepository;
import com.nirmaanwatch.backend.repository.ProgressUpdateRepository;
import com.nirmaanwatch.backend.repository.ProjectRepository;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final ProgressUpdateRepository updates;
    private final UserAccountRepository users;
    private final AuditService auditService;
    private final NotificationService notifications;

    public ProjectService(ProjectRepository projects, ProjectAssignmentRepository assignments,
                          ProgressUpdateRepository updates, UserAccountRepository users,
                          AuditService auditService, NotificationService notifications) {
        this.projects = projects;
        this.assignments = assignments;
        this.updates = updates;
        this.users = users;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    public Page<Project> searchPublic(String query, String department, ProjectStatus status, String location,
                                      Integer minProgress, Integer maxProgress, LocalDate from, LocalDate to,
                                      int page, int size, String sort) {
        Specification<Project> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("publicVisible")));
            addFilters(predicates, cb, root, query, department, status, location, minProgress, maxProgress, from, to);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return projects.findAll(spec, pageable(page, size, sort));
    }

    public Page<Project> searchAll(String query, String department, ProjectStatus status, int page, int size) {
        Specification<Project> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addFilters(predicates, cb, root, query, department, status, null, null, null, null, null);
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
        return projects.findAll(spec, pageable(page, size, "updatedAt,desc"));
    }

    private void addFilters(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                            jakarta.persistence.criteria.Root<Project> root, String query, String department,
                            ProjectStatus status, String location, Integer minProgress, Integer maxProgress,
                            LocalDate from, LocalDate to) {
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("projectCode")), like),
                    cb.like(cb.lower(root.get("department")), like),
                    cb.like(cb.lower(root.get("location")), like)
            ));
        }
        if (department != null && !department.isBlank()) {
            predicates.add(cb.equal(root.get("department"), department));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (location != null && !location.isBlank()) {
            predicates.add(cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%"));
        }
        if (minProgress != null) {
            predicates.add(cb.ge(root.get("progressPercent"), minProgress));
        }
        if (maxProgress != null) {
            predicates.add(cb.le(root.get("progressPercent"), maxProgress));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("expectedCompletionDate"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("expectedCompletionDate"), to));
        }
    }

    private Pageable pageable(int page, int size, String sort) {
        Sort parsed = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",", 2);
            parsed = Sort.by("desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC, parts[0]);
        }
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), parsed);
    }

    public Project requireVisible(Long id, UserAccount viewer) {
        Project project = projects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project was not found."));
        if (project.isPublicVisible()) {
            return project;
        }
        if (viewer == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project was not found.");
        }
        if (viewer.getRole() == Role.ADMIN || isAssigned(project.getId(), viewer.getId())) {
            return project;
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Project was not found.");
    }

    public boolean isAssigned(Long projectId, Long userId) {
        return assignments.existsByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectAssignment> assignmentsFor(Long projectId) {
        return assignments.findByProjectId(projectId);
    }

    public List<Project> assignedProjects(UserAccount officer) {
        return assignments.findByUserId(officer.getId()).stream().map(ProjectAssignment::getProject).toList();
    }

    public List<ProgressUpdate> history(Long projectId) {
        return updates.findByProjectIdOrderByUpdateDateDescCreatedAtDesc(projectId);
    }

    public List<ProgressUpdate> recentOfficerUpdates(Long officerId) {
        return updates.findTop8ByOfficerIdOrderByCreatedAtDesc(officerId);
    }

    @Transactional
    public Project create(ApiDtos.CreateProjectRequest request, UserAccount actor) {
        if (projects.existsByProjectCode(request.projectCode().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "A project with this code already exists.");
        }
        Project project = new Project();
        apply(project, request.name(), request.description(), request.department(), request.location(),
                request.approvedBudget(), request.progressPercent(), request.status(), request.publicVisible(),
                request.startDate(), request.expectedCompletionDate(), request.actualCompletionDate());
        project.setProjectCode(request.projectCode().trim().toUpperCase());
        Project saved = projects.save(project);
        auditService.record(actor, "PROJECT_CREATED", "Project", saved.getId().toString(), saved.getProjectCode());
        return saved;
    }

    @Transactional
    public Project update(Long id, ApiDtos.UpdateProjectRequest request, UserAccount actor) {
        Project project = requireManaged(id, actor);
        apply(project, request.name(), request.description(), request.department(), request.location(),
                request.approvedBudget(), request.progressPercent(), request.status(), request.publicVisible(),
                request.startDate(), request.expectedCompletionDate(), request.actualCompletionDate());
        Project saved = projects.save(project);
        auditService.record(actor, "PROJECT_UPDATED", "Project", saved.getId().toString(), saved.getProjectCode());
        return saved;
    }

    @Transactional
    public ProgressUpdate updateProgress(Long id, ApiDtos.ProgressUpdateRequest request, UserAccount actor) {
        Project project = requireManaged(id, actor);
        if (request.progressPercent() < 0 || request.progressPercent() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Progress must be between 0 and 100.");
        }
        project.setProgressPercent(request.progressPercent());
        project.setStatus(request.status());
        if (request.status() == ProjectStatus.COMPLETED) {
            project.setActualCompletionDate(request.actualCompletionDate() != null ? request.actualCompletionDate() : request.updateDate());
        } else if (request.actualCompletionDate() != null) {
            project.setActualCompletionDate(request.actualCompletionDate());
        }
        projects.save(project);

        ProgressUpdate update = new ProgressUpdate();
        update.setProject(project);
        update.setOfficer(actor);
        update.setProgressPercent(request.progressPercent());
        update.setStatus(request.status());
        update.setDescription(request.description().trim());
        update.setMilestone(request.milestone());
        update.setUpdateDate(request.updateDate());
        ProgressUpdate saved = updates.save(update);
        auditService.record(actor, "PROGRESS_UPDATED", "Project", project.getId().toString(),
                "progress=" + request.progressPercent());
        return saved;
    }

    @Transactional
    public void delete(Long id, UserAccount actor) {
        if (actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can delete projects.");
        }
        Project project = projects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project was not found."));
        assignments.deleteAll(assignments.findByProjectId(id));
        projects.delete(project);
        auditService.record(actor, "PROJECT_DELETED", "Project", id.toString(), project.getProjectCode());
    }

    @Transactional
    public ProjectAssignment assign(Long projectId, Long userId, UserAccount actor) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project was not found."));
        UserAccount officer = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (officer.getRole() != Role.OFFICER && officer.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only officers or administrators can be assigned to projects.");
        }
        if (assignments.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This user is already assigned to the project.");
        }
        ProjectAssignment assignment = new ProjectAssignment();
        assignment.setProject(project);
        assignment.setUser(officer);
        assignment.setAssignedBy(actor);
        ProjectAssignment saved = assignments.save(assignment);
        notifications.notify(officer, "Project assigned", "You were assigned to " + project.getProjectCode() + ".");
        auditService.record(actor, "PROJECT_ASSIGNED", "Project", projectId.toString(), officer.getUsername());
        return saved;
    }

    @Transactional
    public void unassign(Long projectId, Long userId, UserAccount actor) {
        if (!assignments.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment was not found.");
        }
        assignments.deleteByProjectIdAndUserId(projectId, userId);
        auditService.record(actor, "PROJECT_UNASSIGNED", "Project", projectId.toString(), String.valueOf(userId));
    }

    public Project requireManaged(Long id, UserAccount actor) {
        Project project = projects.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project was not found."));
        if (actor.getRole() == Role.ADMIN) {
            return project;
        }
        if (actor.getRole() == Role.OFFICER && isAssigned(id, actor.getId())) {
            return project;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "You can only change projects assigned to you.");
    }

    private void apply(Project project, String name, String description, String department, String location,
                       java.math.BigDecimal budget, int progress, ProjectStatus status, boolean visible,
                       LocalDate start, LocalDate expected, LocalDate actual) {
        if (progress < 0 || progress > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Progress must be between 0 and 100.");
        }
        project.setName(name.trim());
        project.setDescription(description.trim());
        project.setDepartment(department.trim());
        project.setLocation(location.trim());
        project.setApprovedBudget(budget);
        project.setProgressPercent(progress);
        project.setStatus(status);
        project.setPublicVisible(visible);
        project.setStartDate(start);
        project.setExpectedCompletionDate(expected);
        project.setActualCompletionDate(actual);
    }
}
