package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordForm {

    @NotBlank
    @Size(max = 128)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPasswordConfirm;

    public ChangePasswordForm() {
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getNewPasswordConfirm() { return newPasswordConfirm; }
    public void setNewPasswordConfirm(String newPasswordConfirm) { this.newPasswordConfirm = newPasswordConfirm; }
}
