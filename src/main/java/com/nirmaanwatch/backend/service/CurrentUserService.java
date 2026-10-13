package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final UserAccountRepository users;

    public CurrentUserService(UserAccountRepository users) {
        this.users = users;
    }

    public Optional<UserAccount> findCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        return users.findByUsername(authentication.getName());
    }

    public UserAccount requireCurrent() {
        return findCurrent().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
    }
}
