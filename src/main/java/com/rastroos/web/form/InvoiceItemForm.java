package com.rastroos.web.form;

import java.math.BigDecimal;

import com.rastroos.domain.service.InvoiceLineType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Uma linha da fatura na conferência. Os campos vêm da leitura e voltam em
 * campos ocultos; o servidor valida tudo de novo e refaz o cruzamento antes de
 * gravar, então nada aqui é confiado só porque veio da tela.
 */
public class InvoiceItemForm {

    private boolean selected;

    @Size(max = 10)
    private String dateLabel;

    @NotBlank
    @Size(max = 200)
    private String description;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    @Min(1)
    @Max(99)
    private Short installment;

    @Min(2)
    @Max(99)
    private Short installments;

    @NotNull
    private InvoiceLineType type;

    @NotBlank
    @Size(max = 40)
    private String categoryId;

    public InvoiceItemForm() {
    }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public String getDateLabel() { return dateLabel; }
    public void setDateLabel(String dateLabel) { this.dateLabel = dateLabel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Short getInstallment() { return installment; }
    public void setInstallment(Short installment) { this.installment = installment; }

    public Short getInstallments() { return installments; }
    public void setInstallments(Short installments) { this.installments = installments; }

    public InvoiceLineType getType() { return type; }
    public void setType(InvoiceLineType type) { this.type = type; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    /** Parcela vem em par (atual e total) e a atual não passa do total. */
    @AssertTrue
    public boolean isInstallmentConsistent() {
        if (installment == null && installments == null) {
            return true;
        }
        return installment != null && installments != null && installment <= installments;
    }
}
