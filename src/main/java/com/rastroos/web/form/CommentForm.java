package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form de resposta em um chamado. Só o corpo do comentário; autor e papel
 * são resolvidos no Service a partir do usuário autenticado.
 */
public class CommentForm {

    @NotBlank
    @Size(min = 1, max = 4000)
    private String body;

    public CommentForm() {
    }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
