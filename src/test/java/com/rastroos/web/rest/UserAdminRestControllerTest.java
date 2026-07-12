package com.rastroos.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.exception.ResourceNotFoundException;
import com.rastroos.domain.service.UserAdminService;
import com.rastroos.domain.service.UserAdminService.CreateResult;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CurrentUser;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutChecker;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.dto.UserRowDto;

@WebMvcTest(controllers = UserAdminRestController.class,
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class,
            org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        BruteForceFilter.class,
                        LockoutPreAuthFilter.class,
                        LockoutChecker.class,
                        LoginSuccessHandler.class,
                        LoginFailureHandler.class,
                        CustomUserDetailsService.class,
                        AuditLogger.class
                }))
@AutoConfigureMockMvc(addFilters = false)
class UserAdminRestControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserAdminService service;
    @MockitoBean private CurrentUser currentUser;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(adminId);
    }

    @Test
    void listRetornaJson() throws Exception {
        UserRowDto row = new UserRowDto(
                targetId, "Maria", "maria@example.com", true,
                UserRole.USER, UserStatus.ACTIVE,
                Instant.parse("2026-05-01T10:00:00Z"), null, 0L);
        when(service.list(any(), any(), eq(0), eq(20)))
                .thenReturn(new UserAdminListView(List.of(row), 0, 20, 1, 1, 5, 3, 1, 1, 1));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("maria@example.com"))
                .andExpect(jsonPath("$.adminCount").value(1));
    }

    @Test
    void getInexistenteRetorna404() throws Exception {
        when(service.detail(eq(targetId), eq(adminId)))
                .thenThrow(new ResourceNotFoundException("users.notFound"));

        mvc.perform(get("/api/admin/users/{id}", targetId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createValidoRetornaDetalhe() throws Exception {
        User u = new User();
        u.setId(targetId);
        when(service.create(any())).thenReturn(new CreateResult(u, List.of()));
        when(service.detail(eq(targetId), eq(adminId))).thenReturn(detailView());

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Maria",
                "email", "maria@example.com",
                "password", "Abcdef1!",
                "role", "USER",
                "status", "ACTIVE"));

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.toString()))
                .andExpect(jsonPath("$.email").value("maria@example.com"));
    }

    @Test
    void createSenhaFracaRetorna422() throws Exception {
        when(service.create(any()))
                .thenReturn(new CreateResult(null, List.of("password.tooShort")));

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Maria",
                "email", "maria@example.com",
                "password", "weak",
                "role", "USER",
                "status", "ACTIVE"));

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void createEmailEmUsoRetorna409() throws Exception {
        when(service.create(any())).thenThrow(new BusinessRuleException("users.emailTaken"));

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Maria",
                "email", "maria@example.com",
                "password", "Abcdef1!",
                "role", "USER",
                "status", "ACTIVE"));

        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRetornaDetalhe() throws Exception {
        when(service.update(eq(targetId), eq(adminId), any())).thenReturn(new User());
        when(service.detail(eq(targetId), eq(adminId))).thenReturn(detailView());

        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Maria",
                "email", "maria@example.com",
                "role", "USER",
                "status", "ACTIVE"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/admin/users/{id}", targetId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.toString()));
    }

    @Test
    void changeStatusRetornaDetalhe() throws Exception {
        when(service.changeStatus(eq(targetId), eq(adminId), eq(UserStatus.DISABLED)))
                .thenReturn(new User());
        when(service.detail(eq(targetId), eq(adminId))).thenReturn(detailView());

        mvc.perform(patch("/api/admin/users/{id}/status", targetId).param("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@example.com"));
    }

    @Test
    void resetPasswordRetornaSenhaTemporaria() throws Exception {
        when(service.resetPassword(targetId)).thenReturn("Temp1!abcd");

        mvc.perform(post("/api/admin/users/{id}/reset-password", targetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("Temp1!abcd"));
    }

    @Test
    void deleteRetorna204() throws Exception {
        mvc.perform(delete("/api/admin/users/{id}", targetId))
                .andExpect(status().isNoContent());
        verify(service).delete(targetId, adminId);
    }

    private UserDetailView detailView() {
        return new UserDetailView(
                targetId, "Maria", "maria@example.com", true,
                UserRole.USER, UserStatus.ACTIVE, "pt-BR",
                Instant.parse("2026-05-01T10:00:00Z"),
                Instant.parse("2026-05-02T10:00:00Z"), null,
                false, false, List.of(), List.of());
    }
}
