package com.rastroos.web.form;

import com.rastroos.domain.entity.enums.AccountKind;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AccountForm {

    @NotBlank
    @Size(min = 1, max = 80)
    private String name;

    @NotNull
    private AccountKind kind;

    /** Cor em hex no formato {@code #RRGGBB}. Aceita também a cor sem #. */
    @Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "account.colorHexInvalid")
    @Size(max = 7)
    private String colorHex = "#6366f1";

    @Size(max = 8)
    private String iconText;

    @Min(value = 1, message = "account.dayRange")
    @Max(value = 31, message = "account.dayRange")
    private Short closeDay;

    @Min(value = 1, message = "account.dayRange")
    @Max(value = 31, message = "account.dayRange")
    private Short dueDay;

    @Size(max = 40)
    private String categoryId;

    private boolean fixed;

    public AccountForm() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AccountKind getKind() { return kind; }
    public void setKind(AccountKind kind) { this.kind = kind; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    public String getIconText() { return iconText; }
    public void setIconText(String iconText) { this.iconText = iconText; }

    public Short getCloseDay() { return closeDay; }
    public void setCloseDay(Short closeDay) { this.closeDay = closeDay; }

    public Short getDueDay() { return dueDay; }
    public void setDueDay(Short dueDay) { this.dueDay = dueDay; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }
}
