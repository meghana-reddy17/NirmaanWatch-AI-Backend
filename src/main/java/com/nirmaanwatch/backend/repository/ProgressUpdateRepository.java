package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.ProgressUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgressUpdateRepository extends JpaRepository<ProgressUpdate, Long> {
    List<ProgressUpdate> findByProjectIdOrderByUpdateDateDescCreatedAtDesc(Long projectId);
    Optional<ProgressUpdate> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProgressUpdate> findTop8ByOfficerIdOrderByCreatedAtDesc(Long officerId);
}
