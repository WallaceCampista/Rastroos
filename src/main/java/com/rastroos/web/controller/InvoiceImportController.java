package com.rastroos.web.controller;

import java.time.YearMonth;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rastroos.domain.exception.InvalidUploadException;
import com.rastroos.domain.exception.InvoiceReadException;
import com.rastroos.domain.service.InvoiceImportService;
import com.rastroos.security.CurrentUser;
import com.rastroos.web.dto.InvoiceImportResult;
import com.rastroos.web.form.InvoiceImportForm;
import com.rastroos.web.support.PeriodResolver;

import jakarta.validation.Valid;

/**
 * Anexar fatura no cartão de crédito: lê o arquivo, mostra a conferência e
 * lança os itens marcados. As três rotas devolvem o corpo do modal
 * ({@code app/invoice-review}), exceto o lançamento, que redireciona para a
 * tela de cartões com o detalhe da conta aberto de novo.
 *
 * <p>Leitura de fatura usa a IA: só para quem tem o Alfredo liberado, e nunca
 * para acessor (é escrita).
 */
@Controller
@RequestMapping("/app/cards/{id}/invoice")
@PreAuthorize("isAuthenticated() and !hasRole('ACESSOR') and @currentUser.hasAiAccess()")
public class InvoiceImportController {

    private final CurrentUser currentUser;
    private final InvoiceImportService service;
    private final PeriodResolver periodResolver;

    public InvoiceImportController(CurrentUser currentUser, InvoiceImportService service,
                                   PeriodResolver periodResolver) {
        this.currentUser = currentUser;
        this.service = service;
        this.periodResolver = periodResolver;
    }

    /**
     * O Spring limita a 256 os itens de lista criados no bind; uma fatura grande
     * passaria disso e estouraria. O teto real é o do formulário.
     */
    @InitBinder("invoiceImportForm")
    void allowLongInvoices(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(InvoiceImportForm.MAX_ITEMS + 1);
    }

    @PostMapping("/extract")
    public String extract(@PathVariable UUID id,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          @RequestParam(value = "ym", required = false) String ym,
                          Model model) {
        UUID userId = currentUser.requireEffectiveId();
        YearMonth period = periodResolver.resolve(ym);
        try {
            model.addAttribute("review", service.read(userId, id, file, period));
        } catch (InvalidUploadException | InvoiceReadException e) {
            model.addAttribute("review", service.failure(userId, id, e.getMessage()));
        }
        return "app/invoice-review";
    }

    /** Recalcula a conferência depois que a pessoa corrigiu o vencimento. */
    @PostMapping("/review")
    public String review(@PathVariable UUID id,
                         @Valid @ModelAttribute("invoiceImportForm") InvoiceImportForm form,
                         BindingResult binding,
                         Model model) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            model.addAttribute("review", service.failure(userId, id, "account.invoice.invalid"));
            return "app/invoice-review";
        }
        model.addAttribute("review", service.review(userId, id, form));
        model.addAttribute("recalculated", true);
        return "app/invoice-review";
    }

    @PostMapping("/import")
    public String importInvoice(@PathVariable UUID id,
                                @Valid @ModelAttribute("invoiceImportForm") InvoiceImportForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes flash) {
        UUID userId = currentUser.requireEffectiveId();
        if (binding.hasErrors()) {
            model.addAttribute("review", service.failure(userId, id, "account.invoice.invalid"));
            return "app/invoice-review";
        }
        InvoiceImportResult result = service.commit(userId, id, form);
        flash.addFlashAttribute("importResult", result);
        // Destino montado só com dados do servidor: o mês da fatura e o id da conta.
        return "redirect:/app/cards?ym=" + result.month() + "&open=" + id;
    }
}
