package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edição do perfil do próprio usuário. Por ora apenas o nome de exibição — o
 * e-mail é mostrado somente-leitura (trocar e-mail exige re-verificação).
 */
public class ProfileForm {

    @NotBlank
    @Size(max = 120)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
