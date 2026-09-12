package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Primeiro passo do wizard: nome de exibição e — opcionalmente — uma senha
 * nova.
 *
 * <p>A senha é opcional, mas trocá-la continua exigindo a senha atual: uma
 * sessão aberta esquecida numa máquina alheia não pode virar posse
 * permanente da conta.
 */
public class OnboardingProfileForm {

    @NotBlank
    @Size(min = 1, max = 120)
    private String name;

    @Size(max = 128)
    private String currentPassword;

    @Size(max = 128)
    private String newPassword;

    @Size(max = 128)
    private String newPasswordConfirm;

    public OnboardingProfileForm() {
    }

    /** {@code true} quando o usuário preencheu algum campo de senha. */
    public boolean wantsPasswordChange() {
        return notBlank(newPassword) || notBlank(newPasswordConfirm) || notBlank(currentPassword);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getNewPasswordConfirm() { return newPasswordConfirm; }
    public void setNewPasswordConfirm(String newPasswordConfirm) {
        this.newPasswordConfirm = newPasswordConfirm;
    }
}
