package com.rastroos.web.dto;

import java.util.UUID;

/**
 * Filtros opcionais da listagem de transações. Qualquer campo nulo ou
 * em branco é ignorado.
 */
public record TransactionFilter(
        PaidFilter paid,
        UUID accountId,
        String categoryId,
        FixedFilter fixed,
        String search
) {

    public static TransactionFilter empty() {
        return new TransactionFilter(PaidFilter.ALL, null, null, FixedFilter.ALL, null);
    }

    public enum PaidFilter {
        ALL, PAID, UNPAID;

        public static PaidFilter parse(String value) {
            if (value == null || value.isBlank()) return ALL;
            try {
                return PaidFilter.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ALL;
            }
        }
    }

    public enum FixedFilter {
        ALL, FIXED, ONE_OFF;

        public static FixedFilter parse(String value) {
            if (value == null || value.isBlank()) return ALL;
            try {
                return FixedFilter.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ALL;
            }
        }
    }
}
