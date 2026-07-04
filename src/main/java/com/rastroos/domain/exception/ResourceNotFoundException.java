package com.rastroos.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Sinaliza recurso inexistente OU pertencente a outro usuário.
 * Tratada como {@code 404 Not Found} para não vazar existência (§2.2 do
 * CLAUDE.md).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
