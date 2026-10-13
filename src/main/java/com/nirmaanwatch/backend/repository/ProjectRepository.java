package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    Optional<Project> findByProjectCode(String projectCode);
    boolean existsByProjectCode(String projectCode);
    long countByPublicVisibleTrue();
    long countByPublicVisibleTrueAndStatus(ProjectStatus status);
    List<Project> findTop6ByPublicVisibleTrueOrderByUpdatedAtDesc();

    @Query("select distinct p.department from Project p where p.publicVisible = true order by p.department")
    List<String> findDistinctPublicDepartments();
}
