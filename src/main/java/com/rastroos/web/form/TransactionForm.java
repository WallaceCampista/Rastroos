package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criar ou atualizar um lançamento (despesa)")
public class TransactionForm {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "Descrição do lançamento", example = "Mercado")
    private String description;

    @NotNull
    @Schema(description = "Conta/cartão de origem", example = "3f2504e0-4f89-11d3-9a0c-0305e82c3301")
    private UUID accountId;

    @NotBlank
    @Size(max = 40)
    @Schema(description = "Identificador da categoria", example = "food")
    private String categoryId;

    @NotNull
    @DecimalMin(value = "0.01", message = "transaction.amountPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Valor em reais, com 2 casas decimais", example = "249.90")
    private BigDecimal amount;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Vencimento (ISO-8601)", example = "2026-05-10")
    private LocalDate dueDate;

    @Schema(description = "Marca como gasto fixo/recorrente", example = "false")
    private boolean fixed;

    @Schema(description = "Marca como já pago", example = "false")
    private boolean paid;

    /**
     * Quando > 1, criamos N transações com {@code installmentCurrent} 1..N
     * e {@code installmentTotal=N}, com {@code dueDate} + i meses.
     */
    @Min(1)
    @Max(60)
    @Schema(description = "Número de parcelas (1 = à vista); gera N lançamentos mensais", example = "1")
    private int installments = 1;

    public TransactionForm() {
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public int getInstallments() { return installments; }
    public void setInstallments(int installments) { this.installments = installments; }
}
