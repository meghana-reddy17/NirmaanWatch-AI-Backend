package com.nirmaanwatch.backend.service;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService auditService;

    public UserAdminService(UserAccountRepository users, PasswordEncoder encoder, AuditService auditService) {
        this.users = users;
        this.encoder = encoder;
        this.auditService = auditService;
    }

    public Page<UserAccount> search(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            return users.findAll(PageRequest.of(page, Math.min(size, 50)));
        }
        String q = query.trim();
        return users.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                q, q, q, PageRequest.of(page, Math.min(size, 50)));
    }

    @Transactional
    public UserAccount create(ApiDtos.CreateUserRequest request, UserAccount actor) {
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is already in use.");
        }
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already in use.");
        }
        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setEnabled(true);
        user.setMustChangePassword(true);
        UserAccount saved = users.save(user);
        auditService.record(actor, "USER_CREATED", "User", saved.getId().toString(), saved.getUsername() + ":" + saved.getRole());
        return saved;
    }

    @Transactional
    public UserAccount update(Long id, ApiDtos.UpdateUserRequest request, UserAccount actor) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (user.getRole() == Role.ADMIN && request.role() != Role.ADMIN) {
            preventLastAdminLoss();
        }
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        UserAccount saved = users.save(user);
        auditService.record(actor, "USER_UPDATED", "User", id.toString(), saved.getRole().name());
        return saved;
    }

    @Transactional
    public UserAccount setEnabled(Long id, boolean enabled, UserAccount actor) {
        UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User was not found."));
        if (user.getId().equals(actor.getId()) && !enabled) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account.");
        }
        if (user.getRole() == Role.ADMIN && !enabled) {
            preventLastAdminLoss();
        }
        user.setEnabled(enabled);
        UserAccount saved = users.save(user);
        auditService.record(actor, enabled ? "USER_ACTIVATED" : "USER_DEACTIVATED", "User", id.toString(), user.getUsername());
        return saved;
    }

    private void preventLastAdminLoss() {
        if (users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "The last active administrator cannot be removed or demoted.");
        }
    }
}
