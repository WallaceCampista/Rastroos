package com.rastroos.web.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Dados brutos de uma tela, já formatados como pares rótulo/valor, mais o
 * resumo determinístico calculado a partir deles.
 *
 * <p>Os números vêm sempre do {@code fallbackText} e das {@code lines} — a IA
 * só reescreve o texto a partir daí, nunca busca dado por conta própria. Isso
 * mantém o resumo verificável mesmo quando o motor de IA está ligado.
 *
 * @param screen       tela de origem
 * @param periodLabel  rótulo do período (ex.: {@code "setembro de 2026"}) ou
 *                     {@code null} nas telas sem mês
 * @param lines        pares {@code "Rótulo: valor"} usados como contexto da IA
 * @param fallbackText resumo determinístico (situação + cuidados)
 */
public record InsightFacts(
        InsightScreen screen,
        String periodLabel,
        List<String> lines,
        String fallbackText
) {

    /**
     * Impressão digital dos números desta tela (SHA-256 em hexa).
     *
     * <p>É a chave que decide se um resumo já gerado continua valendo: mesmo
     * hash, mesmo dado, nenhuma chamada nova ao provedor. Cobre
     * <strong>todas</strong> as linhas, e não só o texto final — dois estados
     * diferentes podem produzir a mesma frase de resumo, e nesse caso o texto
     * sozinho não perceberia a mudança.
     */
    public String fingerprint() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(screen.key()).append('\n').append(periodLabel).append('\n');
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        sb.append(fallbackText);
        return sha256(sb.toString());
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
