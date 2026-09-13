package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A conferência da fatura, reenviada para recalcular (vencimento trocado) ou
 * para lançar os itens marcados.
 */
public class InvoiceImportForm {

    /** Máximo de linhas aceitas — o mesmo teto da leitura ({@code ai.invoice.max-items}). */
    public static final int MAX_ITEMS = 300;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    /** {@code true} quando o vencimento foi lido do documento (senão a tela pede para conferir). */
    private boolean dueDateRead;

    /** Total impresso na fatura — só exibição. */
    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal totalRead;

    /** 4 dígitos impressos na fatura — só exibição (aviso de cartão trocado). */
    @Pattern(regexp = "^(\\d{4})?$")
    private String last4Read;

    @Valid
    @Size(max = MAX_ITEMS)
    private List<InvoiceItemForm> items = new ArrayList<>();

    public InvoiceImportForm() {
    }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public boolean isDueDateRead() { return dueDateRead; }
    public void setDueDateRead(boolean dueDateRead) { this.dueDateRead = dueDateRead; }

    public BigDecimal getTotalRead() { return totalRead; }
    public void setTotalRead(BigDecimal totalRead) { this.totalRead = totalRead; }

    public String getLast4Read() { return last4Read; }
    public void setLast4Read(String last4Read) { this.last4Read = last4Read; }

    public List<InvoiceItemForm> getItems() { return items; }
    public void setItems(List<InvoiceItemForm> items) { this.items = items; }
}
