package com.rastroos.web.form;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de uma receita recorrente (salário fixo de uma empresa). "
        + "O pagamento é marcado por dia útil, não por dia do calendário.")
public class IncomeSourceForm {

    @NotBlank
    @Size(min = 1, max = 120)
    @Schema(description = "Empresa que paga", example = "Acme Ltda")
    private String name;

    @NotNull
    @DecimalMin(value = "0.01", message = "income.amountPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Valor mensal em reais", example = "5000.00")
    private BigDecimal amount;

    @NotNull
    @Min(value = 1, message = "incomeSource.payBusinessDayRange")
    @Max(value = 23, message = "incomeSource.payBusinessDayRange")
    @Schema(description = "Em qual dia útil do mês o pagamento cai (1–23)", example = "5")
    private Short payBusinessDay;

    /** Mês do primeiro pagamento; nulo = mês corrente. */
    @DateTimeFormat(pattern = "yyyy-MM")
    @Schema(description = "Mês do primeiro pagamento (AAAA-MM); vazio = mês corrente", example = "2026-09")
    private YearMonth startMonth;

    @Size(max = 200)
    @Schema(description = "Observação livre (opcional)")
    private String note;

    public IncomeSourceForm() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Short getPayBusinessDay() { return payBusinessDay; }
    public void setPayBusinessDay(Short payBusinessDay) { this.payBusinessDay = payBusinessDay; }

    public YearMonth getStartMonth() { return startMonth; }
    public void setStartMonth(YearMonth startMonth) { this.startMonth = startMonth; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
