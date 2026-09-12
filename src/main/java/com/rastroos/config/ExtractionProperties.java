package com.rastroos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guarda-corpos de <strong>upload</strong> da extração de gasto por
 * documento/foto. Vale mesmo com a IA desligada: o arquivo é validado antes de
 * qualquer decisão sobre chamar (ou não) o modelo de visão.
 *
 * <p>A configuração do modelo em si (habilitado, tokens, detalhe da imagem)
 * mora em {@link AiProperties.Vision} — uma credencial só para toda a IA.
 */
@ConfigurationProperties(prefix = "extraction")
public class ExtractionProperties {

    /** Tamanho máximo aceito por arquivo (bytes), além do limite do multipart. */
    private long maxFileSizeBytes = 8L * 1024 * 1024;

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
}
