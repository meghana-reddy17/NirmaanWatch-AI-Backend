package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.entity.AppNotification;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.repository.AppNotificationRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final AppNotificationRepository notifications;

    public NotificationService(AppNotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notify(UserAccount user, String title, String message) {
        AppNotification notification = new AppNotification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notifications.save(notification);
    }
}
