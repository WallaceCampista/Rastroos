package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulário do modal "Resetar senha" (tela Usuários): o admin define uma nova
 * senha para outro usuário. A força é validada pela {@code PasswordPolicy} no
 * serviço; o alvo é obrigado a trocá-la no próximo login.
 */
public class UserResetPasswordForm {

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPasswordConfirm;

    public UserResetPasswordForm() {
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getNewPasswordConfirm() { return newPasswordConfirm; }
    public void setNewPasswordConfirm(String newPasswordConfirm) { this.newPasswordConfirm = newPasswordConfirm; }
}
