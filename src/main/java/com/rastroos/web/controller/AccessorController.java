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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.entity.AccessorRequest;
import com.rastroos.domain.service.AccessorService;
import com.rastroos.security.AuditLogger;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.form.AccessorRequestForm;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Tela /app/accessors — do <em>titular</em>. Transparência (quantos/quais
 * acessores tem, mesmo criados pelo admin), privacidade (ocultar valores por
 * acessor) e solicitação de novos acessores. Bloqueada para contas ACESSOR.
 */
@Controller
@RequestMapping("/app/accessors")
@PreAuthorize("isAuthenticated() and !hasRole('ACESSOR')")
public class AccessorController {

    private final CurrentUser currentUser;
    private final AccessorService accessors;
    private final AuditLogger audit;

    public AccessorController(CurrentUser currentUser,
                              AccessorService accessors,
                              AuditLogger audit) {
        this.currentUser = currentUser;
        this.accessors = accessors;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        populate(model);
        if (!model.containsAttribute("requestForm")) {
            model.addAttribute("requestForm", new AccessorRequestForm());
        }
        return "app/accessors";
    }

    @PostMapping("/request")
    public String request(@Valid @ModelAttribute("requestForm") AccessorRequestForm form,
                          BindingResult binding,
                          Model model,
                          HttpServletRequest httpRequest,
                          RedirectAttributes flash) {
        if (binding.hasErrors()) {
            populate(model);
            return "app/accessors";
        }
        AccessorRequest r = accessors.createRequest(currentUser.requireId(),
                form.getAccessorName(), form.getAccessorEmail(), form.getNote());
        audit.record(currentUser.requireId(), "ACCESSOR_REQUEST_CREATE", "accessor_request",
                r.getId().toString(), httpRequest, null);
        flash.addFlashAttribute("ok", "accessors.requestCreated");
        return "redirect:/app/accessors";
    }

    @PostMapping("/{id}/mask")
    public String mask(@PathVariable UUID id, HttpServletRequest httpRequest, RedirectAttributes flash) {
        accessors.setValuesMasked(currentUser.requireId(), id, true);
        audit.record(currentUser.requireId(), "ACCESSOR_MASK", "user", id.toString(), httpRequest, null);
        flash.addFlashAttribute("ok", "accessors.masked");
        return "redirect:/app/accessors";
    }

    @PostMapping("/{id}/unmask")
    public String unmask(@PathVariable UUID id, HttpServletRequest httpRequest, RedirectAttributes flash) {
        accessors.setValuesMasked(currentUser.requireId(), id, false);
        audit.record(currentUser.requireId(), "ACCESSOR_UNMASK", "user", id.toString(), httpRequest, null);
        flash.addFlashAttribute("ok", "accessors.unmasked");
        return "redirect:/app/accessors";
    }

    private void populate(Model model) {
        UUID me = currentUser.requireId();
        model.addAttribute("activeNav", "accessors");
        model.addAttribute("myAccessors", accessors.myAccessors(me));
        model.addAttribute("myRequests", accessors.myRequests(me));
    }
}
