package com.rastroos.domain.service;

/**
 * Origem do arquivo enviado para extração de um gasto:
 * <ul>
 *   <li>{@link #DOCUMENT} — documento importado (PDF/imagem): boleto, fatura,
 *       comprovante. Extrai valor, nome da conta, vencimento…</li>
 *   <li>{@link #RECEIPT} — foto da notinha da maquininha: extrai valor, data,
 *       hora e os 4 últimos dígitos do cartão (casados com a conta do usuário).</li>
 * </ul>
 */
public enum ExpenseExtractionSource {
    DOCUMENT,
    RECEIPT;

    /** Parse leniente do parâmetro da requisição; desconhecido → {@link #DOCUMENT}. */
    public static ExpenseExtractionSource parse(String raw) {
        if (raw == null) {
            return DOCUMENT;
        }
        return "receipt".equalsIgnoreCase(raw.trim()) ? RECEIPT : DOCUMENT;
    }
}
