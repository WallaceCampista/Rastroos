package com.rastroos.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyForm {

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "verify.codeFormat")
    private String code;

    public VerifyForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
