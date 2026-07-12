package com.rastroos.web.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.entity.AccessorRequest;
import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;
import com.rastroos.domain.exception.BusinessRuleException;
import com.rastroos.domain.service.AccessorService;
import com.rastroos.domain.service.UserAdminService;
import com.rastroos.domain.service.UserAdminService.CreateResult;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.UserAdminListView;
import com.rastroos.web.dto.UserDetailView;
import com.rastroos.web.dto.UserFilter;
import com.rastroos.web.form.UserCreateForm;
import com.rastroos.web.form.UserEditForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) da gestão de usuários — admin-only. Controller fino: valida,
 * delega ao {@link UserAdminService} e escolhe a view. Ações sensíveis são
 * auditadas ({@link AuditLogger}).
 */
@Controller
@RequestMapping("/app/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final CurrentUser currentUser;
    private final UserAdminService service;
    private final AccessorService accessorService;
    private final AuditLogger audit;

    public UserAdminController(CurrentUser currentUser,
                               UserAdminService service,
                               AccessorService accessorService,
                               AuditLogger audit) {
        this.currentUser = currentUser;
        this.service = service;
        this.accessorService = accessorService;
        this.audit = audit;
    }

    @GetMapping
    public String list(@RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                       Model model) {
        UserAdminListView view = service.list(status, q, page, size);
        model.addAttribute("activeNav", "users");
        model.addAttribute("view", view);
        model.addAttribute("filter", new UserFilter(status, q));
        model.addAttribute("statuses", UserStatus.values());
        return "app/users";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(value = "requestId", required = false) UUID requestId,
                          Model model) {
        if (!model.containsAttribute("userForm")) {
            UserCreateForm form = new UserCreateForm();
            if (requestId != null) {          // veio de uma solicitação do titular
                AccessorRequest r = accessorService.requireRequest(requestId);
                form.setName(r.getAccessorName());
                form.setEmail(r.getAccessorEmail());
                form.setRole(UserRole.ACESSOR);
                form.setAccessesUserId(r.getRequesterUserId());
            }
            model.addAttribute("userForm", form);
        }
        model.addAttribute("requestId", requestId);
        prepareFormModel(model, false, null);
        return "app/user-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("userForm") UserCreateForm form,
                         BindingResult binding,
                         @RequestParam(value = "requestId", required = false) UUID requestId,
                         Model model,
                         HttpServletRequest request,
                         RedirectAttributes flash) {
        model.addAttribute("requestId", requestId);
        if (!binding.hasFieldErrors("email") && service.isEmailTaken(form.getEmail(), null)) {
            binding.rejectValue("email", "users.emailTaken");
        }
        if (binding.hasErrors()) {
            prepareFormModel(model, false, null);
            return "app/user-form";
        }

        CreateResult result;
        try {
            result = service.create(form);
        } catch (BusinessRuleException e) {
            binding.rejectValue("accessesUserId", e.getMessage());
            prepareFormModel(model, false, null);
            return "app/user-form";
        }
        if (!result.ok()) {
            result.passwordErrors().forEach(err -> binding.rejectValue("password", err));
            prepareFormModel(model, false, null);
            return "app/user-form";
        }

        audit.record(currentUser.requireId(), "USER_CREATE", "user",
                result.user().getId().toString(), request, null);
        if (requestId != null) {              // aprova e vincula a solicitação de origem
            accessorService.markApproved(requestId, currentUser.requireId(), result.user().getId());
        }
        flash.addFlashAttribute("ok", "users.created");
        return "redirect:/app/users/" + result.user().getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        UserDetailView view = service.detail(id, currentUser.requireId());
        model.addAttribute("activeNav", "users");
        model.addAttribute("view", view);
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("accessors", service.accessorsOf(id));
        return "app/user-detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        UserDetailView view = service.detail(id, currentUser.requireId());
        if (!model.containsAttribute("userForm")) {
            UserEditForm form = new UserEditForm();
            form.setName(view.name());
            form.setEmail(view.email());
            form.setRole(view.role());
            form.setStatus(view.status());
            form.setAccessesUserId(view.accessesUserId());
            model.addAttribute("userForm", form);
        }
        prepareFormModel(model, true, view);
        return "app/user-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("userForm") UserEditForm form,
                         BindingResult binding,
                         Model model,
                         HttpServletRequest request,
                         RedirectAttributes flash) {
        if (!binding.hasFieldErrors("email") && service.isEmailTaken(form.getEmail(), id)) {
            binding.rejectValue("email", "users.emailTaken");
        }
        if (binding.hasErrors()) {
            prepareFormModel(model, true, service.detail(id, currentUser.requireId()));
            return "app/user-form";
        }
        try {
            service.update(id, currentUser.requireId(), form);
        } catch (BusinessRuleException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/app/users/" + id + "/edit";
        }
        audit.record(currentUser.requireId(), "USER_UPDATE", "user", id.toString(), request, null);
        flash.addFlashAttribute("ok", "users.updated");
        return "redirect:/app/users/" + id;
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam("status") UserStatus status,
                               HttpServletRequest request,
                               RedirectAttributes flash) {
        try {
            service.changeStatus(id, currentUser.requireId(), status);
            audit.record(currentUser.requireId(), "USER_STATUS_CHANGE", "user", id.toString(),
                    request, "{\"status\":\"" + status.name() + "\"}");
            flash.addFlashAttribute("ok", "users.statusUpdated");
        } catch (BusinessRuleException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/users/" + id;
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id,
                                HttpServletRequest request,
                                RedirectAttributes flash) {
        String temp = service.resetPassword(id);
        audit.record(currentUser.requireId(), "USER_RESET_PASSWORD", "user", id.toString(),
                request, null);
        flash.addFlashAttribute("ok", "users.passwordReset");
        flash.addFlashAttribute("tempPassword", temp);
        return "redirect:/app/users/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id,
                         HttpServletRequest request,
                         RedirectAttributes flash) {
        try {
            service.delete(id, currentUser.requireId());
            audit.record(currentUser.requireId(), "USER_DELETE", "user", id.toString(), request, null);
            flash.addFlashAttribute("ok", "users.deleted");
            return "redirect:/app/users";
        } catch (BusinessRuleException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/app/users/" + id;
        }
    }

    @PostMapping("/{id}/sessions/{sessionId}/revoke")
    public String revokeSession(@PathVariable UUID id,
                                @PathVariable UUID sessionId,
                                HttpServletRequest request,
                                RedirectAttributes flash) {
        boolean revoked = service.terminateSession(id, sessionId);
        audit.record(currentUser.requireId(), "USER_SESSION_REVOKE", "user_session",
                sessionId.toString(), request, null);
        flash.addFlashAttribute(revoked ? "ok" : "error",
                revoked ? "users.sessionRevoked" : "users.sessionNotFound");
        return "redirect:/app/users/" + id;
    }

    @PostMapping("/{id}/sessions/revoke-all")
    public String revokeAllSessions(@PathVariable UUID id,
                                    HttpServletRequest request,
                                    RedirectAttributes flash) {
        service.terminateAllSessions(id);
        audit.record(currentUser.requireId(), "USER_SESSION_REVOKE_ALL", "user", id.toString(),
                request, null);
        flash.addFlashAttribute("ok", "users.sessionsRevoked");
        return "redirect:/app/users/" + id;
    }

    // ── helpers ──────────────────────────────────────────────

    private void prepareFormModel(Model model, boolean edit, UserDetailView target) {
        model.addAttribute("activeNav", "users");
        model.addAttribute("edit", edit);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("target", target);
        model.addAttribute("accessorTargets", service.eligibleTargets());
    }
}
