package com.rastroos.web.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.service.AccessorService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Fila de solicitações de acessor — admin-only. Aprovar é um link para o form
 * de criação de usuário pré-preenchido ({@code /app/users/new?requestId=…}),
 * onde o admin define a senha; a vinculação da solicitação acontece ao criar a
 * conta. Aqui só listamos e rejeitamos.
 */
@Controller
@RequestMapping("/app/accessor-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccessorRequestController {

    private final CurrentUser currentUser;
    private final AccessorService accessors;
    private final AuditLogger audit;

    public AdminAccessorRequestController(CurrentUser currentUser,
                                          AccessorService accessors,
                                          AuditLogger audit) {
        this.currentUser = currentUser;
        this.accessors = accessors;
        this.audit = audit;
    }

    @GetMapping
    public String queue(Model model) {
        model.addAttribute("activeNav", "accessor-requests");
        model.addAttribute("requests", accessors.pendingRequests());
        return "app/accessor-requests";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable UUID id, HttpServletRequest httpRequest, RedirectAttributes flash) {
        accessors.reject(id, currentUser.requireId());
        audit.record(currentUser.requireId(), "ACCESSOR_REQUEST_REJECT", "accessor_request",
                id.toString(), httpRequest, null);
        flash.addFlashAttribute("ok", "accessors.requestRejected");
        return "redirect:/app/accessor-requests";
    }
}
