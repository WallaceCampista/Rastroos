package com.rastroos.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Política de senha: mínimo 8 caracteres, com maiúscula, minúscula,
 * dígito e caractere especial. Use {@link #validate(String)} para
 * obter a lista de problemas (vazia = OK).
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    public List<String> validate(String password) {
        List<String> errors = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            errors.add("password.empty");
            return errors;
        }
        if (password.length() < MIN_LENGTH) errors.add("password.tooShort");
        if (password.length() > MAX_LENGTH) errors.add("password.tooLong");
        if (!password.matches(".*[a-z].*")) errors.add("password.needsLower");
        if (!password.matches(".*[A-Z].*")) errors.add("password.needsUpper");
        if (!password.matches(".*\\d.*"))   errors.add("password.needsDigit");
        if (!password.matches(".*[^a-zA-Z0-9].*")) errors.add("password.needsSpecial");
        return errors;
    }

    public boolean isStrong(String password) {
        return validate(password).isEmpty();
    }
}
