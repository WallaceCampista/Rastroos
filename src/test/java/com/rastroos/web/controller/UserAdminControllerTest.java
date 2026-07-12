package com.rastroos.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.service.AccessorService;
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
import com.rastroos.web.dto.LoginAttemptDto;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.dto.UserRowDto;
import com.rastroos.web.dto.UserSessionDto;

@WebMvcTest(controllers = UserAdminController.class,
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
class UserAdminControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private UserAdminService service;
    @MockitoBean private AccessorService accessorService;
    @MockitoBean private CurrentUser currentUser;
    @MockitoBean private AuditLogger audit;

    private final UUID adminId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUser.requireId()).thenReturn(adminId);
    }

    @Test
    void listRenderizaTabela() throws Exception {
        when(service.list(any(), any(), eq(0), eq(20))).thenReturn(listView());

        mvc.perform(get("/app/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/users"))
                .andExpect(model().attribute("activeNav", "users"))
                .andExpect(model().attributeExists("view", "filter", "statuses"));
    }

    @Test
    void newFormRenderiza() throws Exception {
        mvc.perform(get("/app/users/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/user-form"))
                .andExpect(model().attribute("edit", false))
                .andExpect(model().attributeExists("userForm", "roles", "statuses"));
    }

    @Test
    void createValidoRedirecionaParaDetalhe() throws Exception {
        User created = new User();
        created.setId(targetId);
        when(service.isEmailTaken(eq("maria@example.com"), isNull())).thenReturn(false);
        when(service.create(any())).thenReturn(new CreateResult(created, List.of()));

        mvc.perform(post("/app/users/new")
                        .param("name", "Maria")
                        .param("email", "maria@example.com")
                        .param("password", "Abcdef1!")
                        .param("role", "USER")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users/" + targetId))
                .andExpect(flash().attribute("ok", "users.created"));

        verify(service).create(any());
    }

    @Test
    void createComSenhaFracaVoltaAoForm() throws Exception {
        when(service.isEmailTaken(any(), isNull())).thenReturn(false);
        when(service.create(any()))
                .thenReturn(new CreateResult(null, List.of("password.tooShort")));

        mvc.perform(post("/app/users/new")
                        .param("name", "Maria")
                        .param("email", "maria@example.com")
                        .param("password", "weak")
                        .param("role", "USER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/user-form"));
    }

    @Test
    void createInvalidoBeanValidationVoltaAoForm() throws Exception {
        mvc.perform(post("/app/users/new")
                        .param("name", "")
                        .param("email", "nao-eh-email")
                        .param("password", "")
                        .param("role", "USER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/user-form"));
    }

    @Test
    void detailRenderiza() throws Exception {
        when(service.detail(eq(targetId), eq(adminId))).thenReturn(detailView(false));

        mvc.perform(get("/app/users/{id}", targetId))
                .andExpect(status().isOk())
                .andExpect(view().name("app/user-detail"))
                .andExpect(model().attributeExists("view"));
    }

    @Test
    void editFormRenderizaComEditTrue() throws Exception {
        when(service.detail(eq(targetId), eq(adminId))).thenReturn(detailView(false));

        mvc.perform(get("/app/users/{id}/edit", targetId))
                .andExpect(status().isOk())
                .andExpect(view().name("app/user-form"))
                .andExpect(model().attribute("edit", true))
                .andExpect(model().attributeExists("userForm", "target"));
    }

    @Test
    void updateValidoRedireciona() throws Exception {
        when(service.isEmailTaken(eq("maria@example.com"), eq(targetId))).thenReturn(false);
        when(service.update(eq(targetId), eq(adminId), any())).thenReturn(new User());

        mvc.perform(post("/app/users/{id}/edit", targetId)
                        .param("name", "Maria")
                        .param("email", "maria@example.com")
                        .param("role", "USER")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users/" + targetId))
                .andExpect(flash().attribute("ok", "users.updated"));
    }

    @Test
    void changeStatusRedireciona() throws Exception {
        when(service.changeStatus(eq(targetId), eq(adminId), eq(UserStatus.DISABLED)))
                .thenReturn(new User());

        mvc.perform(post("/app/users/{id}/status", targetId).param("status", "DISABLED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users/" + targetId))
                .andExpect(flash().attribute("ok", "users.statusUpdated"));
    }

    @Test
    void resetPasswordExibeSenhaTemporaria() throws Exception {
        when(service.resetPassword(targetId)).thenReturn("Temp1!abcd");

        mvc.perform(post("/app/users/{id}/reset-password", targetId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users/" + targetId))
                .andExpect(flash().attribute("ok", "users.passwordReset"))
                .andExpect(flash().attribute("tempPassword", "Temp1!abcd"));
    }

    @Test
    void deleteRedirecionaParaLista() throws Exception {
        mvc.perform(post("/app/users/{id}/delete", targetId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users"))
                .andExpect(flash().attribute("ok", "users.deleted"));

        verify(service).delete(targetId, adminId);
    }

    @Test
    void revokeSessionRedirecionaParaDetalhe() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.terminateSession(targetId, sessionId)).thenReturn(true);

        mvc.perform(post("/app/users/{id}/sessions/{sid}/revoke", targetId, sessionId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/users/" + targetId))
                .andExpect(flash().attribute("ok", "users.sessionRevoked"));
    }

    // ── helpers ──────────────────────────────────────────────

    private UserAdminListView listView() {
        UserRowDto row = new UserRowDto(
                targetId, "Maria", "maria@example.com", true,
                UserRole.USER, UserStatus.ACTIVE,
                Instant.parse("2026-05-01T10:00:00Z"),
                Instant.parse("2026-05-02T10:00:00Z"), 1L);
        return new UserAdminListView(List.of(row), 0, 20, 1, 1, 5, 3, 1, 1, 1);
    }

    private UserDetailView detailView(boolean self) {
        UserSessionDto s = new UserSessionDto(
                UUID.randomUUID(), "Firefox", "192.0.2.1",
                Instant.parse("2026-05-01T10:00:00Z"),
                Instant.parse("2026-05-01T11:00:00Z"));
        LoginAttemptDto a = new LoginAttemptDto(
                "192.0.2.1", true, Instant.parse("2026-05-01T10:00:00Z"));
        return new UserDetailView(
                targetId, "Maria", "maria@example.com", true,
                UserRole.USER, UserStatus.ACTIVE, "pt-BR",
                Instant.parse("2026-05-01T10:00:00Z"),
                Instant.parse("2026-05-02T10:00:00Z"),
                Instant.parse("2026-05-02T12:00:00Z"),
                false, self, List.of(s), List.of(a), null, null);
    }
}
