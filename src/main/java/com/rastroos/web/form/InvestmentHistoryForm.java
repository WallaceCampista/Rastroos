package com.rastroos.web.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Form para adicionar/atualizar um ponto do histórico mensal de um
 * investimento.
 */
public class InvestmentHistoryForm {

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "investment.yearMonthFormat")
    private String yearMonth;

    @NotNull
    @PositiveOrZero(message = "investment.amountPositive")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    public InvestmentHistoryForm() {
    }

    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
