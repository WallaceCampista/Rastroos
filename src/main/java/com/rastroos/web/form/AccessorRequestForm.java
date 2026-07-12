package com.rastroos.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form do titular solicitando ao admin a criação de uma conta acessor com
 * acesso aos seus dados. O admin revisa e aprova (cria a conta) ou rejeita.
 */
public class AccessorRequestForm {

    @NotBlank
    @Size(max = 120)
    private String accessorName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String accessorEmail;

    @Size(max = 500)
    private String note;

    public AccessorRequestForm() {
    }

    public String getAccessorName() { return accessorName; }
    public void setAccessorName(String accessorName) { this.accessorName = accessorName; }

    public String getAccessorEmail() { return accessorEmail; }
    public void setAccessorEmail(String accessorEmail) { this.accessorEmail = accessorEmail; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
