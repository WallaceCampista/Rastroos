package com.rastroos.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Violação de uma regra de negócio que o cliente poderia, em tese, corrigir:
 * email já em uso, tentativa de remover o último admin, auto-destruição de
 * conta, etc. Mapeada para {@code 409 Conflict}. A {@code message} carrega uma
 * chave i18n para a camada Web resolver.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
