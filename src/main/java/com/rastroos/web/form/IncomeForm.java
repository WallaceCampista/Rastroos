package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Lançamento de receita. Com uma fonte recorrente cadastrada basta escolher a
 * empresa ({@code sourceId}) e o valor — a origem vem do cadastro. Sem fonte,
 * a origem é digitada em {@code source}. Receita não tem categoria.
 */
@Schema(description = "Dados para criar ou atualizar uma receita")
public class IncomeForm {

    @Schema(description = "Id da fonte recorrente cadastrada (opcional)")
    private UUID sourceId;

    @Size(max = 120)
    @Schema(description = "Origem digitada, quando não há fonte cadastrada", example = "Freela")
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

    @Size(max = 200)
    @Schema(description = "Observação livre (opcional)")
    private String note;

    public IncomeForm() {
    }

    /**
     * Origem é obrigatória, mas pode chegar de dois jeitos. A checagem vive
     * na Bean Validation (e não só no service) para o REST devolver 400 com o
     * campo apontado, em vez de estourar da regra de negócio.
     */
    @AssertTrue(message = "income.sourceRequired")
    @Schema(hidden = true)
    public boolean isSourceProvided() {
        return sourceId != null || (source != null && !source.isBlank());
    }

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getIncomeDate() { return incomeDate; }
    public void setIncomeDate(LocalDate incomeDate) { this.incomeDate = incomeDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
