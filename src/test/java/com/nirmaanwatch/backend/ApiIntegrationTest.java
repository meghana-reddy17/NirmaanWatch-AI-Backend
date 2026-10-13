package com.nirmaanwatch.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirmaanwatch.backend.entity.Project;
import com.nirmaanwatch.backend.entity.ProjectStatus;
import com.nirmaanwatch.backend.entity.Role;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.repository.ProjectRepository;
import com.nirmaanwatch.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserAccountRepository users;
    @Autowired
    ProjectRepository projects;
    @Autowired
    PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        if (users.findByUsername("admin").isEmpty()) {
            saveUser("admin", "admin@test.local", "Admin", Role.ADMIN, "AdminPass_123");
        }
        if (users.findByUsername("officer").isEmpty()) {
            saveUser("officer", "officer@test.local", "Officer", Role.OFFICER, "OfficerPass_123");
        }
        if (users.findByUsername("citizen").isEmpty()) {
            saveUser("citizen", "citizen@test.local", "Citizen", Role.CITIZEN, "CitizenPass_123");
        }
        if (projects.findByProjectCode("NW-001").isEmpty()) {
            Project project = new Project();
            project.setProjectCode("NW-001");
            project.setName("Government Primary Health Center");
            project.setDescription("Test project");
            project.setDepartment("Health Department");
            project.setLocation("Narsapur");
            project.setApprovedBudget(new BigDecimal("25000000"));
            project.setProgressPercent(15);
            project.setStatus(ProjectStatus.IN_PROGRESS);
            project.setPublicVisible(true);
            project.setStartDate(LocalDate.of(2025, 4, 1));
            project.setExpectedCompletionDate(LocalDate.of(2027, 3, 31));
            projects.save(project);
        }
    }

    @Test
    void bcryptStoresHashedPasswords() {
        UserAccount admin = users.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(admin.getPasswordHash()).doesNotContain("AdminPass_123");
        assertThat(encoder.matches("AdminPass_123", admin.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong", admin.getPasswordHash())).isFalse();
    }

    @Test
    void loginSuccessAndFailure() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "AdminPass_123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "nope"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicProjectListingAndInvalidProgress() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectCode").value("NW-001"));

        String cookie = login("admin", "AdminPass_123");
        mockMvc.perform(patch("/api/projects/1/progress").with(csrf()).cookie(session(cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progressPercent":150,"status":"IN_PROGRESS","description":"bad","updateDate":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void citizenCannotAccessAdmin() throws Exception {
        String cookie = login("citizen", "CitizenPass_123");
        mockMvc.perform(get("/api/admin/users").cookie(session(cookie)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects").with(csrf()).cookie(session(cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectCode":"NW-X","name":"X","description":"X","department":"X","location":"X","approvedBudget":1,"progressPercent":0,"status":"PLANNED","publicVisible":true,"startDate":"2026-01-01","expectedCompletionDate":"2027-01-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotAccessAdminUsers() throws Exception {
        String cookie = login("officer", "OfficerPass_123");
        mockMvc.perform(get("/api/admin/users").cookie(session(cookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportSubmissionAndProjectCreate() throws Exception {
        mockMvc.perform(post("/api/reports").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":1,"category":"DELAY","subject":"Work appears stalled","description":"No visible progress for weeks.","issueLocation":"Main gate","anonymous":true,"consentAcknowledged":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicReference").exists())
                .andExpect(jsonPath("$.trackingToken").exists());

        String cookie = login("admin", "AdminPass_123");
        mockMvc.perform(post("/api/projects").with(csrf()).cookie(session(cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectCode":"NW-TEST","name":"Test Road","description":"Created by test","department":"Roads and Buildings","location":"Hyderabad","approvedBudget":100000,"progressPercent":0,"status":"PLANNED","publicVisible":true,"startDate":"2026-01-01","expectedCompletionDate":"2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectCode").value("NW-TEST"));
    }

    private void saveUser(String username, String email, String name, Role role, String password) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setPasswordHash(encoder.encode(password));
        users.save(user);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("NIRMAANWATCH_SESSION").getValue();
    }

    private jakarta.servlet.http.Cookie session(String value) {
        return new jakarta.servlet.http.Cookie("NIRMAANWATCH_SESSION", value);
    }
}
