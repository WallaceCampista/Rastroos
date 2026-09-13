package com.rastroos.web.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Qual motor de IA passa a valer para a instalação. Nunca carrega chave. */
@Schema(description = "Motor de IA a ativar")
public class AiProviderForm {

    @NotBlank
    @Size(max = 30)
    // Lista fechada de caracteres (§3.3): o valor vira chave de busca do
    // fornecedor e nome em log.
    @Pattern(regexp = "^[a-z0-9-]{1,30}$", message = "ai.providerUnknown")
    @Schema(description = "Identificador do fornecedor", example = "gemini")
    private String provider;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}
