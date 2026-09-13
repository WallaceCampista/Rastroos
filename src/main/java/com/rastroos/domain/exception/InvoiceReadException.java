package com.rastroos.domain.exception;

/**
 * A fatura não pôde ser lida (IA desligada, teto diário, provedor fora, PDF com
 * senha, nada legível). Sem {@code @ResponseStatus}: a camada Web mostra a
 * mensagem no próprio modal, sem página de erro (§4.1, degradar sempre).
 * A {@code message} carrega uma chave i18n.
 */
public class InvoiceReadException extends RuntimeException {

    public InvoiceReadException(String messageKey) {
        super(messageKey);
    }
}
