package com.rastroos.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lê o cabeçalho {@code User-Agent} já no tamanho que a coluna aceita.
 *
 * <p>O cabeçalho é controlado pelo cliente e não tem limite no protocolo: um
 * User-Agent maior que a coluna derruba o INSERT — e como o registro acontece
 * dentro do handler de login, isso quebraria o próprio login (inclusive o de
 * uma tentativa que falhou, alcançável sem autenticação).
 */
final class RequestUserAgent {

    private RequestUserAgent() {
    }

    /**
     * @param request requisição em curso
     * @param maxLength tamanho da coluna de destino
     * @return o User-Agent truncado, ou {@code null} se ausente/vazio
     */
    static String of(HttpServletRequest request, int maxLength) {
        String raw = request.getHeader("User-Agent");
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
