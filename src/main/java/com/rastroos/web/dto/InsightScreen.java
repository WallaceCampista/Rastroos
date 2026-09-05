package com.rastroos.web.dto;

import java.util.Optional;

/**
 * Telas que têm resumo do Alfredo (widget flutuante). A chave espelha o
 * {@code activeNav} publicado pelos controllers, que é o que o JS envia na
 * URL — telas fora desta lista não têm resumo (o orbe vira só atalho de chat).
 *
 * <p>A tela do próprio Alfredo ({@code manager}) não entra: lá o chat já é a
 * tela inteira.
 */
public enum InsightScreen {

    DASHBOARD("dashboard", "Visão geral", true),
    CARDS("cards", "Cartões & Contas", true),
    EXPENSES("expenses", "Gastos Variáveis", true),
    INCOME("income", "Receitas", true),
    INVESTMENTS("investments", "Investimentos", false),
    REPORTS("reports", "Relatórios", true),
    COMPARE("compare", "Comparativo", true);

    private final String key;
    private final String label;
    private final boolean periodic;

    InsightScreen(String key, String label, boolean periodic) {
        this.key = key;
        this.label = label;
        this.periodic = periodic;
    }

    /** Chave usada na URL e no {@code data-screen} do widget. */
    public String key() {
        return key;
    }

    /** Rótulo exibível (título do balão e prefixo do título da conversa). */
    public String label() {
        return label;
    }

    /** {@code false} quando o resumo não depende do mês selecionado. */
    public boolean isPeriodic() {
        return periodic;
    }

    public static Optional<InsightScreen> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String needle = value.trim().toLowerCase();
        for (InsightScreen screen : values()) {
            if (screen.key.equals(needle)) {
                return Optional.of(screen);
            }
        }
        return Optional.empty();
    }
}
