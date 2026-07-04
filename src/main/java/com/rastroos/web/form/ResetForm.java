package com.rastroos.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ResetForm {

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "verify.codeFormat")
    private String code;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPasswordConfirm;

    public ResetForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getNewPasswordConfirm() { return newPasswordConfirm; }
    public void setNewPasswordConfirm(String newPasswordConfirm) { this.newPasswordConfirm = newPasswordConfirm; }
}
