package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.DtoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final CurrentUserService currentUserService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserAccountRepository users,
                          CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName());
    }

    @PostMapping("/login")
    public ApiDtos.UserDto login(@Valid @RequestBody ApiDtos.LoginRequest request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        UserAccount user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
        user.setLastLoginAt(Instant.now());
        users.save(user);
        return DtoMapper.toUser(user);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("message", "Signed out.");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return currentUserService.findCurrent()
                .<Map<String, Object>>map(user -> Map.of("authenticated", true, "user", DtoMapper.toUser(user)))
                .orElseGet(() -> Map.of("authenticated", false));
    }
}
