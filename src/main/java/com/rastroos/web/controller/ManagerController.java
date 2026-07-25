package com.rastroos.web.controller;

import java.time.Clock;
import java.time.YearMonth;
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

import com.rastroos.domain.service.ChatService;
import com.rastroos.domain.service.DashboardService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.DashboardModel;
import com.rastroos.web.dto.ManagerView;
import com.rastroos.web.form.ChatPromptForm;

import jakarta.validation.Valid;

/**
 * Web (Thymeleaf) para /app/manager — chat com o Alfredo (gerente IA):
 * histórico de conversas, thread e envio de mensagens. Toda operação é
 * escopada ao usuário corrente pelo {@link ChatService}.
 */
@Controller
@RequestMapping("/app/manager")
@PreAuthorize("isAuthenticated()")
public class ManagerController {

    private final CurrentUser currentUser;
    private final ChatService service;
    private final DashboardService dashboard;
    private final Clock clock;

    public ManagerController(CurrentUser currentUser, ChatService service,
                             DashboardService dashboard, Clock clock) {
        this.currentUser = currentUser;
        this.service = service;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @GetMapping
    public String manager(@RequestParam(value = "chat", required = false) String chat,
                          Model model) {
        UUID userId = currentUser.requireId();
        ManagerView view = service.load(userId, parseUuid(chat));

        model.addAttribute("activeNav", "manager");
        model.addAttribute("view", view);
        if (!model.containsAttribute("promptForm")) {
            model.addAttribute("promptForm", new ChatPromptForm());
        }
        // Quadro financeiro (canvas à direita): snapshot do mês atual.
        if (view.hasActiveChat()) {
            DashboardModel snap = dashboard.load(userId, YearMonth.now(clock));
            model.addAttribute("canvasKpis", snap.kpis());
            model.addAttribute("canvasCats", snap.byCategory().stream().limit(5).toList());
        }
        return "app/manager";
    }

    @PostMapping("/start")
    public String startEmpty() {
        UUID chatId = service.startEmpty(currentUser.requireId());
        return "redirect:/app/manager?chat=" + chatId;
    }

    @PostMapping("/new")
    public String start(@Valid @ModelAttribute("promptForm") ChatPromptForm form,
                        BindingResult binding,
                        RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "chat.empty");
            return "redirect:/app/manager";
        }
        UUID chatId = service.start(currentUser.requireId(), form.getMessage());
        return "redirect:/app/manager?chat=" + chatId;
    }

    @PostMapping("/{id}/messages")
    public String send(@PathVariable UUID id,
                       @Valid @ModelAttribute("promptForm") ChatPromptForm form,
                       BindingResult binding,
                       RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("error", "chat.empty");
            return "redirect:/app/manager?chat=" + id;
        }
        service.send(currentUser.requireId(), id, form.getMessage());
        return "redirect:/app/manager?chat=" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        service.delete(currentUser.requireId(), id);
        flash.addFlashAttribute("ok", "chat.deleted");
        return "redirect:/app/manager";
    }

    /** UUID inválido ou ausente → {@code null} (tela de boas-vindas). */
    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
