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

import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;
import com.rastroos.domain.service.SupportService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.SupportFilter;
import com.rastroos.web.dto.SupportListView;
import com.rastroos.web.dto.TicketDetailView;
import com.rastroos.web.form.CommentForm;
import com.rastroos.web.form.TicketForm;
import com.rastroos.web.form.TicketStatusForm;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/support — abertura, listagem e thread de
 * chamados. Usuário comum vê os próprios; admin vê todos e muda status.
 */
@Controller
@RequestMapping("/app/support")
@PreAuthorize("isAuthenticated()")
public class SupportController {

    private final CurrentUser currentUser;
    private final SupportService service;

    public SupportController(CurrentUser currentUser, SupportService service) {
        this.currentUser = currentUser;
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "category", required = false) String category,
                       @RequestParam(value = "scope", required = false) String scope,
                       @RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                       @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                       Model model) {
        boolean admin = currentUser.isAdmin();
        SupportFilter filter = new SupportFilter(status, priority, category, scope, q);
        SupportListView view = service.list(currentUser.requireId(), admin,
                status, priority, category, filter.isMine(), q, page, size);

        model.addAttribute("activeNav", "support");
        model.addAttribute("view", view);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", SupportTicketStatus.values());
        return "app/support";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", emptyForm());
        }
        prepareFormModel(model);
        return "app/support-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("ticketForm") TicketForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes flash) {
        if (binding.hasErrors()) {
            prepareFormModel(model);
            return "app/support-form";
        }
        service.create(currentUser.requireId(), form);
        flash.addFlashAttribute("ok", "support.created");
        return "redirect:/app/support";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        boolean admin = currentUser.isAdmin();
        TicketDetailView view = service.detail(currentUser.requireId(), admin, id);

        model.addAttribute("activeNav", "support");
        model.addAttribute("view", view);
        if (!model.containsAttribute("commentForm")) {
            model.addAttribute("commentForm", new CommentForm());
        }
        if (!model.containsAttribute("statusForm")) {
            TicketStatusForm sf = new TicketStatusForm();
            sf.setStatus(view.status());
            model.addAttribute("statusForm", sf);
        }
        model.addAttribute("statuses", SupportTicketStatus.values());
        return "app/support-detail";
    }

    @PostMapping("/{id}/comments")
    public String comment(@PathVariable String id,
                          @Valid @ModelAttribute("commentForm") CommentForm form,
                          BindingResult binding,
                          RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "support.commentEmpty");
            return "redirect:/app/support/" + id;
        }
        try {
            service.addComment(currentUser.requireId(), currentUser.isAdmin(), id, form.getBody());
            flash.addFlashAttribute("ok", "support.commentAdded");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/support/" + id;
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public String changeStatus(@PathVariable String id,
                               @Valid @ModelAttribute("statusForm") TicketStatusForm form,
                               BindingResult binding,
                               RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "support.statusInvalid");
            return "redirect:/app/support/" + id;
        }
        service.updateStatus(currentUser.isAdmin(), id, form.getStatus());
        flash.addFlashAttribute("ok", "support.statusUpdated");
        return "redirect:/app/support/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String id, RedirectAttributes flash) {
        try {
            service.cancel(currentUser.requireId(), currentUser.isAdmin(), id);
            flash.addFlashAttribute("ok", "support.canceled");
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/support/" + id;
    }

    // ── helpers ──────────────────────────────────────────────

    private void prepareFormModel(Model model) {
        model.addAttribute("activeNav", "support");
        model.addAttribute("categories", SupportTicketCategory.values());
        model.addAttribute("priorities", SupportTicketPriority.values());
    }

    private TicketForm emptyForm() {
        TicketForm f = new TicketForm();
        f.setCategory(SupportTicketCategory.BUG);
        f.setPriority(SupportTicketPriority.MEDIUM);
        return f;
    }
}
