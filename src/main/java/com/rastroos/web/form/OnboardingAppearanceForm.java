package com.rastroos.web.form;

import com.rastroos.domain.entity.enums.UserTheme;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Segundo passo do wizard: tema claro/escuro e a paleta de cores. */
public class OnboardingAppearanceForm {

    @NotNull
    private UserTheme theme = UserTheme.dark;

    @Min(value = 0, message = "onboarding.paletteInvalid")
    @Max(value = 17, message = "onboarding.paletteInvalid")
    private short paletteIndex;

    public OnboardingAppearanceForm() {
    }

    public UserTheme getTheme() { return theme; }
    public void setTheme(UserTheme theme) { this.theme = theme; }

    public short getPaletteIndex() { return paletteIndex; }
    public void setPaletteIndex(short paletteIndex) { this.paletteIndex = paletteIndex; }
}
