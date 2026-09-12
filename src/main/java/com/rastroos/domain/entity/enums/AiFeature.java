package com.rastroos.domain.entity.enums;

/**
 * Funcionalidade de IA que originou uma chamada ao provedor. Serve para
 * separar custo por uso e para dar a cada uma seu próprio circuit breaker.
 */
public enum AiFeature {

    /** Conversa com o Alfredo (tela do gerente e chat flutuante). */
    CHAT,

    /** Resumo de tela do balão flutuante. */
    INSIGHT,

    /** Vetorização de texto para a busca semântica. */
    EMBEDDING,

    /** Leitura de boleto/fatura/notinha por visão. */
    VISION
}
