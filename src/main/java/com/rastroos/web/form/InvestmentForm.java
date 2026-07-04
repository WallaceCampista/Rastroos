package com.rastroos.web.form;

import java.math.BigDecimal;

import com.rastroos.domain.entity.enums.InvestmentKind;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class InvestmentForm {

    @NotBlank
    @Size(min = 1, max = 120)
    private String name;

    @NotNull
    private InvestmentKind kind;

    @NotNull
    @PositiveOrZero(message = "investment.amountPositive")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    /** Apenas para cofrinhos. */
    @DecimalMin(value = "0.01", message = "investment.goalPositive")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal goal;

    @Size(max = 60)
    private String rateLabel;

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    private BigDecimal monthlyReturn;

    /** Cor sólida (#aabbcc) ou expressão CSS aceita pelo banco. */
    @Size(max = 80)
    private String colorHex;

    @Pattern(regexp = "^[\\p{Alnum}\\p{Punct}\\s]{0,8}$", message = "investment.iconInvalid")
    @Size(max = 8)
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
