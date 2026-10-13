package com.nirmaanwatch.backend.repository;

import com.nirmaanwatch.backend.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    List<AppNotification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
