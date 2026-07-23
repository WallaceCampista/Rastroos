package com.rastroos.web.form;

import java.math.BigDecimal;

import com.rastroos.domain.entity.enums.InvestmentKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criar ou atualizar um investimento (cofrinho ou carteira)")
public class InvestmentForm {

    @NotBlank
    @Size(min = 1, max = 120)
    @Schema(description = "Nome do investimento", example = "Reserva de emergência")
    private String name;

    @NotNull
    @Schema(description = "Tipo do investimento", example = "PIGGY")
    private InvestmentKind kind;

    @NotNull
    @PositiveOrZero(message = "investment.amountPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Montante aplicado, em reais", example = "1000.00")
    private BigDecimal amount;

    /** Apenas para cofrinhos. */
    @DecimalMin(value = "0.01", message = "investment.goalPositive")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Meta em reais (apenas cofrinhos PIGGY)", example = "5000.00")
    private BigDecimal goal;

    @Size(max = 60)
    @Schema(description = "Rótulo de rentabilidade (livre)", example = "110% do CDI")
    private String rateLabel;

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Rendimento mensal estimado, em reais", example = "12.50")
    private BigDecimal monthlyReturn;

    /** Cor sólida (#aabbcc) ou expressão CSS aceita pelo banco. */
    @Size(max = 80)
    @Schema(description = "Cor de destaque (hex ou expressão CSS)", example = "#22c55e")
    private String colorHex = "#6366f1";

    // Ícone curto: letras/números/pontuação/símbolos Unicode (◆ ★ ♥ … e emojis)
    // e espaços. \p{Alnum}/\p{Punct} do POSIX são só ASCII — quebravam os símbolos.
    @Pattern(regexp = "^[\\p{L}\\p{N}\\p{P}\\p{S}\\s]{0,8}$", message = "investment.iconInvalid")
    @Size(max = 8)
    @Schema(description = "Ícone curto/emoji (até 8 chars)", example = "🐷")
    private String iconText;

    public InvestmentForm() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public InvestmentKind getKind() { return kind; }
    public void setKind(InvestmentKind kind) { this.kind = kind; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getGoal() { return goal; }
    public void setGoal(BigDecimal goal) { this.goal = goal; }

    public String getRateLabel() { return rateLabel; }
    public void setRateLabel(String rateLabel) { this.rateLabel = rateLabel; }

    public BigDecimal getMonthlyReturn() { return monthlyReturn; }
    public void setMonthlyReturn(BigDecimal monthlyReturn) { this.monthlyReturn = monthlyReturn; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconText() { return iconText; }
    public void setIconText(String iconText) { this.iconText = iconText; }
}
