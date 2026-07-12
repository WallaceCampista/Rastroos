package com.rastroos.web.form;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Form para adicionar/atualizar um ponto do histórico mensal de um
 * investimento.
 */
@Schema(description = "Ponto mensal do histórico de um investimento (upsert por ano-mês)")
public class InvestmentHistoryForm {

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "investment.yearMonthFormat")
    @Schema(description = "Ano-mês no formato YYYY-MM", example = "2026-05")
    private String yearMonth;

    @NotNull
    @PositiveOrZero(message = "investment.amountPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Saldo do investimento naquele mês, em reais", example = "1250.00")
    private BigDecimal amount;

    public InvestmentHistoryForm() {
    }

    public String getYearMonth() { return yearMonth; }
    public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
