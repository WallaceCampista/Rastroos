package com.rastroos.domain.exception;

/**
 * Arquivo de upload inválido na extração de gasto (vazio, grande demais ou
 * formato não suportado). Sem {@code @ResponseStatus}: a camada Web trata
 * localmente, re-renderizando o formulário com uma mensagem amigável.
 * A {@code message} carrega uma chave i18n.
 */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String messageKey) {
        super(messageKey);
    }
}
