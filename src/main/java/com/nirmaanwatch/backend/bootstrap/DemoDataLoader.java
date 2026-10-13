package com.nirmaanwatch.backend.bootstrap;

import com.nirmaanwatch.backend.config.AppProperties;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectAssignment;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.repository.ProjectAssignmentRepository;
import com.nirmaanwatch.backend.repository.ProjectRepository;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

@Component
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final AppProperties properties;
    private final Environment environment;
    private final UserAccountRepository users;
    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final PasswordEncoder encoder;

    public DemoDataLoader(AppProperties properties, Environment environment, UserAccountRepository users,
                          ProjectRepository projects, ProjectAssignmentRepository assignments, PasswordEncoder encoder) {
        this.properties = properties;
        this.environment = environment;
        this.users = users;
        this.projects = projects;
        this.assignments = assignments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        bootstrapAdminIfRequested();
        if (!properties.isSeedDemo()) {
            return;
        }
        UserAccount admin = ensureUser("admin", "admin@nirmaanwatch.local", "Demonstration Administrator", Role.ADMIN);
        UserAccount officer = ensureUser("officer", "officer@nirmaanwatch.local", "Demonstration Officer", Role.OFFICER);
        ensureUser("citizen", "citizen@nirmaanwatch.local", "Demonstration Citizen", Role.CITIZEN);

        Project phc = ensureProject("NW-001", "Government Primary Health Center",
                "Construction and fit-out of a government primary health center serving nearby villages. This record is demonstration data for local setup.",
                "Health Department", "Narsapur, Medak District", new BigDecimal("25000000.00"), 15,
                ProjectStatus.IN_PROGRESS, LocalDate.of(2025, 4, 1), LocalDate.of(2027, 3, 31));
        ensureProject("NW-002", "Rural Piped Water Supply",
                "Demonstration project for a rural water distribution network.",
                "Public Health Engineering", "Zaheerabad", new BigDecimal("18000000.00"), 42,
                ProjectStatus.IN_PROGRESS, LocalDate.of(2024, 8, 1), LocalDate.of(2026, 12, 31));
        ensureProject("NW-003", "School Additional Classrooms",
                "Demonstration project adding classrooms to an existing government school.",
                "Education Department", "Siddipet", new BigDecimal("9500000.00"), 100,
                ProjectStatus.COMPLETED, LocalDate.of(2023, 6, 1), LocalDate.of(2025, 3, 31));
        Project delayed = ensureProject("NW-004", "Village Approach Road Strengthening",
                "Demonstration project currently behind the published schedule.",
                "Roads and Buildings", "Gajwel", new BigDecimal("12000000.00"), 28,
                ProjectStatus.DELAYED, LocalDate.of(2024, 1, 15), LocalDate.of(2025, 10, 31));
        delayed.setActualCompletionDate(null);
        projects.save(delayed);

        if (!assignments.existsByProjectIdAndUserId(phc.getId(), officer.getId())) {
            ProjectAssignment assignment = new ProjectAssignment();
            assignment.setProject(phc);
            assignment.setUser(officer);
            assignment.setAssignedBy(admin);
            assignments.save(assignment);
        }
        log.info("Demonstration accounts are available in this development profile. Change the temporary password immediately. See documentation/DATABASE_SETUP.md.");
    }

    private void bootstrapAdminIfRequested() {
        String username = environment.getProperty("NIRMAANWATCH_BOOTSTRAP_ADMIN_USERNAME");
        String password = environment.getProperty("NIRMAANWATCH_BOOTSTRAP_ADMIN_PASSWORD");
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return;
        }
        if (users.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username.trim());
        admin.setEmail(username.trim() + "@local.bootstrap");
        admin.setFullName("Bootstrapped Administrator");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(encoder.encode(password));
        admin.setMustChangePassword(true);
        users.save(admin);
        log.info("Created the first administrator from bootstrap environment variables. Change the password after first login.");
    }

    private UserAccount ensureUser(String username, String email, String name, Role role) {
        return users.findByUsername(username).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setUsername(username);
            user.setEmail(email);
            user.setFullName(name);
            user.setRole(role);
            user.setPasswordHash(encoder.encode(properties.getDemoPassword()));
            user.setMustChangePassword(true);
            return users.save(user);
        });
    }

    private Project ensureProject(String code, String name, String description, String department, String location,
                                  BigDecimal budget, int progress, ProjectStatus status, LocalDate start, LocalDate expected) {
        return projects.findByProjectCode(code).orElseGet(() -> {
            Project project = new Project();
            project.setProjectCode(code);
            project.setName(name);
            project.setDescription(description);
            project.setDepartment(department);
            project.setLocation(location);
            project.setApprovedBudget(budget);
            project.setProgressPercent(progress);
            project.setStatus(status);
            project.setPublicVisible(true);
            project.setStartDate(start);
            project.setExpectedCompletionDate(expected);
            if (status == ProjectStatus.COMPLETED) {
                project.setActualCompletionDate(expected);
            }
            return projects.save(project);
        });
    }
}
