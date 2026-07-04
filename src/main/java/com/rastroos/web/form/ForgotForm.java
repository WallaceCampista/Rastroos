package com.rastroos.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ForgotForm {

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    public ForgotForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
