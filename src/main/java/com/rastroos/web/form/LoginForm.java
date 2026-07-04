package com.rastroos.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginForm {

    @NotBlank
    @Email
    @Size(max = 180)
    private String username;

    @NotBlank
    @Size(min = 1, max = 128)
    private String password;

    public LoginForm() {
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
