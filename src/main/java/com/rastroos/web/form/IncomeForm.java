package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criar ou atualizar uma receita")
public class IncomeForm {

    @NotBlank
    @Size(min = 1, max = 120)
    @Schema(description = "Fonte da receita", example = "Salário")
    private String source;

    @NotNull
    @DecimalMin(value = "0.01", message = "income.amountPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Valor em reais, com 2 casas decimais", example = "3500.00")
    private BigDecimal amount;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Data da receita (ISO-8601)", example = "2026-05-05")
    private LocalDate incomeDate;

    @Size(max = 40)
    @Schema(description = "Identificador da categoria (opcional)", example = "salary")
    private String categoryId;

    @Size(max = 200)
    @Schema(description = "Observação livre (opcional)")
    private String note;

    public IncomeForm() {
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
