package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncomeForm {

    @NotBlank
    @Size(min = 1, max = 120)
    private String source;

    @NotNull
    @DecimalMin(value = "0.01", message = "income.amountPositive")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate incomeDate;

    @Size(max = 40)
    private String categoryId;

    @Size(max = 200)
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
