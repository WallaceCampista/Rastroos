package com.rastroos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void senhaCompletaPassa() {
        assertThat(policy.isStrong("Forte!2026")).isTrue();
        assertThat(policy.validate("Forte!2026")).isEmpty();
    }

    @Test
    void senhaVaziaFalha() {
        assertThat(policy.validate("")).contains("password.empty");
        assertThat(policy.validate(null)).contains("password.empty");
    }

    @Test
    void senhaCurtaFalha() {
        assertThat(policy.validate("Aa1!"))
                .contains("password.tooShort");
    }

    @Test
    void senhaSemMaiusculaFalha() {
        assertThat(policy.validate("forte!2026"))
                .contains("password.needsUpper");
    }

    @Test
    void senhaSemMinusculaFalha() {
        assertThat(policy.validate("FORTE!2026"))
                .contains("password.needsLower");
    }

    @Test
    void senhaSemDigitoFalha() {
        assertThat(policy.validate("ForteSenha!"))
                .contains("password.needsDigit");
    }

    @Test
    void senhaSemEspecialFalha() {
        assertThat(policy.validate("ForteSenha2026"))
                .contains("password.needsSpecial");
    }

    @Test
    void senhaMuitoLongaFalha() {
        String huge = "Aa1!" + "x".repeat(200);
        assertThat(policy.validate(huge)).contains("password.tooLong");
    }
}
