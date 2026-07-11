package com.rastroos.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensagem enviada ao Alfredo (abertura de conversa ou nova pergunta).
 */
public class ChatPromptForm {

    @NotBlank
    @Size(min = 1, max = 2000)
    private String message;

    public ChatPromptForm() {
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
