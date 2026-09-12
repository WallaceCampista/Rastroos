package com.rastroos.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fonte de receita recorrente na camada Web — a empresa cadastrada que o
 * usuário escolhe ao lançar o salário do mês.
 *
 * <p>Os campos {@code current*} descrevem o recebimento <b>do mês exibido</b>:
 * é o que a tela usa para dizer "cairá dia 07" e para oferecer o botão de
 * confirmar o recebimento.
 *
 * @param payBusinessDay  em qual dia útil do mês o pagamento cai (1–23)
 * @param entries         quantos recebimentos já foram gerados por esta fonte
 * @param closedAt        preenchido quando a fonte foi encerrada
 * @param currentIncomeId lançamento do mês exibido; {@code null} se não há
 */
public record IncomeSourceDto(
        UUID id,
        String name,
        BigDecimal amount,
        short payBusinessDay,
        String note,
        LocalDate closedAt,
        long entries,
        UUID currentIncomeId,
        LocalDate currentPayDate,
        boolean currentReceived
) {
    public boolean active() {
        return closedAt == null;
    }

    /** {@code true} quando há um recebimento desta fonte no mês exibido. */
    public boolean hasCurrent() {
        return currentIncomeId != null;
    }
}
