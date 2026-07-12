package com.rastroos.domain.entity.enums;

/**
 * Ciclo de vida de uma solicitação de criação de acessor feita pelo titular.
 * O admin resolve a solicitação (aprova → cria a conta; ou rejeita).
 */
public enum AccessorRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
