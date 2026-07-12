package com.rastroos.domain.entity.enums;

public enum UserRole {
    USER,
    ADMIN,
    /** Conta que opera os dados financeiros de um usuário-alvo (sem poder excluir). */
    ACESSOR
}
