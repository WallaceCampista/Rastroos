package com.rastroos.domain.service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import com.rastroos.domain.entity.Transaction;

/**
 * Cruza as linhas de uma fatura com o que já está lançado no cartão, para que
 * importar nunca duplique: nem a mesma fatura duas vezes, nem a do mês
 * seguinte — que repete as parcelas já projetadas pela anterior.
 *
 * <p>Cada lançamento existente casa com <strong>no máximo uma</strong> linha
 * (duas compras iguais no mesmo dia são duas linhas e precisam de dois
 * lançamentos). O cruzamento é feito em passadas, da mais exigente para a mais
 * frouxa, para que um par exato nunca perca o lançamento para um par aproximado:
 *
 * <ol>
 *   <li><b>já lançado</b>: mesmo mês da fatura, mesma parcela (ou ambos sem
 *       parcela), mesmo valor e descrição equivalente;</li>
 *   <li><b>ajustar valor</b>: parcela igual com diferença de centavos
 *       (arredondamento do banco) — o valor da fatura prevalece, se ainda
 *       não estiver pago;</li>
 *   <li><b>possível duplicado</b>: mesmo mês, parcela e valor, mas descrição
 *       diferente — tipicamente algo que a pessoa lançou à mão com outro nome.
 *       Quem decide é ela, na conferência.</li>
 * </ol>
 *
 * <p>O resto é novo. Para uma compra parcelada nova, as parcelas seguintes são
 * planejadas nas faturas futuras, reaproveitando as que já existirem (por
 * exemplo, quando uma fatura mais recente foi importada antes).
 *
 * <p>Classe sem estado compartilhado nem acesso a banco: quem chama carrega os
 * lançamentos e grava o resultado.
 */
final class InvoiceReconciler {

    enum Status { NEW, EXISTING, ADJUST, POSSIBLE_DUPLICATE }

    /** Linha a cruzar. {@code installment}/{@code installments} nulos = compra à vista. */
    record Line(String description, long amountCents, Short installment, Short installments) {
        boolean isInstallment() {
            return installment != null && installments != null && installments > 1;
        }
    }

    /** Parcela seguinte de uma compra: já lançada ({@code existing}) ou a criar. */
    record Future(short number, LocalDate dueDate, Transaction existing) {
        boolean toCreate() {
            return existing == null;
        }
    }

    private static final double MIN_SIMILARITY = 0.8;
    private static final int MIN_CONTAINED_LENGTH = 4;

    private final List<Line> lines;
    private final LocalDate dueDate;
    private final YearMonth month;
    private final List<Transaction> pool;
    private final Status[] statuses;
    private final Transaction[] matches;

    private InvoiceReconciler(List<Line> lines, LocalDate dueDate, Collection<Transaction> existing) {
        this.lines = List.copyOf(lines);
        this.dueDate = dueDate;
        this.month = YearMonth.from(dueDate);
        this.pool = new ArrayList<>(existing);
        // Ordem estável: o mesmo banco dá sempre o mesmo pareamento, na
        // conferência e na gravação.
        this.pool.sort(Comparator.comparing(Transaction::getDueDate)
                .thenComparing(Transaction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> String.valueOf(t.getId())));
        this.statuses = new Status[lines.size()];
        this.matches = new Transaction[lines.size()];
    }

    /**
     * @param dueDate  vencimento da fatura: define o mês em que as linhas caem
     * @param existing lançamentos do cartão do mês da fatura em diante
     */
    static InvoiceReconciler reconcile(List<Line> lines, LocalDate dueDate, Collection<Transaction> existing) {
        InvoiceReconciler r = new InvoiceReconciler(lines, dueDate, existing);
        r.pass(Status.EXISTING);
        r.pass(Status.ADJUST);
        r.pass(Status.POSSIBLE_DUPLICATE);
        for (int i = 0; i < r.statuses.length; i++) {
            if (r.statuses[i] == null) {
                r.statuses[i] = Status.NEW;
            }
        }
        return r;
    }

    Status status(int index) {
        return statuses[index];
    }

    /** O lançamento existente que casou com a linha, ou {@code null} se ela é nova. */
    Transaction match(int index) {
        return matches[index];
    }

    /**
     * Planeja as parcelas seguintes da linha, consumindo as que já existem.
     * Chame uma vez por linha que será gravada, na ordem das linhas — duas
     * compras idênticas não podem reaproveitar a mesma parcela futura.
     */
    List<Future> futures(int index) {
        Line line = lines.get(index);
        if (!line.isInstallment()) {
            return List.of();
        }
        List<Future> planned = new ArrayList<>();
        for (int k = line.installment() + 1; k <= line.installments(); k++) {
            int number = k;
            int offset = number - line.installment();
            YearMonth target = month.plusMonths(offset);
            Transaction existing = take(row -> YearMonth.from(row.getDueDate()).equals(target)
                    && samePosition(row, line.installments(), number)
                    && withinTolerance(line.amountCents(), row.getAmountCents())
                    && similar(line.description(), row.getDescription()));
            planned.add(new Future((short) number,
                    existing != null ? existing.getDueDate() : dueDate.plusMonths(offset),
                    existing));
        }
        return planned;
    }

    // ── Passadas ─────────────────────────────────────────────────────────

    private void pass(Status kind) {
        for (int i = 0; i < lines.size(); i++) {
            if (statuses[i] != null) {
                continue;
            }
            Line line = lines.get(i);
            Transaction found = take(row -> inMonth(row) && matches(kind, line, row));
            if (found != null) {
                statuses[i] = kind == Status.ADJUST && found.isPaid() ? Status.EXISTING : kind;
                matches[i] = found;
            }
        }
    }

    private boolean matches(Status kind, Line line, Transaction row) {
        boolean position = line.isInstallment()
                ? samePosition(row, line.installments(), line.installment())
                : !isInstallment(row);
        if (!position) {
            return false;
        }
        return switch (kind) {
            case EXISTING -> row.getAmountCents() == line.amountCents()
                    && similar(line.description(), row.getDescription());
            // Centavo de diferença só é tolerado em parcela: numa compra à vista,
            // valor diferente é outra compra.
            case ADJUST -> line.isInstallment()
                    && row.getAmountCents() != line.amountCents()
                    && withinTolerance(line.amountCents(), row.getAmountCents())
                    && similar(line.description(), row.getDescription());
            case POSSIBLE_DUPLICATE -> (line.isInstallment()
                    ? withinTolerance(line.amountCents(), row.getAmountCents())
                    : row.getAmountCents() == line.amountCents())
                    && !similar(line.description(), row.getDescription());
            case NEW -> false;
        };
    }

    private Transaction take(Predicate<Transaction> test) {
        for (int i = 0; i < pool.size(); i++) {
            Transaction row = pool.get(i);
            if (test.test(row)) {
                pool.remove(i);
                return row;
            }
        }
        return null;
    }

    private boolean inMonth(Transaction row) {
        return YearMonth.from(row.getDueDate()).equals(month);
    }

    private static boolean isInstallment(Transaction row) {
        return row.getInstallmentTotal() != null && row.getInstallmentTotal() > 1;
    }

    private static boolean samePosition(Transaction row, int installments, int installment) {
        return isInstallment(row)
                && row.getInstallmentTotal() == installments
                && row.getInstallmentCurrent() != null
                && row.getInstallmentCurrent() == installment;
    }

    /**
     * Arredondamento de parcela: até 1% do valor, com piso de 5 centavos.
     * Serve para 100,00 em 3x virar 33,33 + 33,33 + 33,34 sem parecer outra compra.
     */
    static boolean withinTolerance(long expectedCents, long actualCents) {
        long tolerance = Math.max(5L, Math.round(expectedCents * 0.01));
        return Math.abs(expectedCents - actualCents) <= tolerance;
    }

    // ── Descrição ────────────────────────────────────────────────────────

    /**
     * Descrições equivalentes: iguais depois de normalizadas, uma contida na
     * outra (o banco trunca o nome do estabelecimento) ou quase iguais (a
     * leitura trocou uma letra).
     */
    static boolean similar(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        if (x.equals(y)) {
            return true;
        }
        boolean xShorter = x.length() <= y.length();
        String shorter = xShorter ? x : y;
        String longer = xShorter ? y : x;
        if (shorter.length() >= MIN_CONTAINED_LENGTH && longer.contains(shorter)) {
            return true;
        }
        int distance = levenshtein(x, y);
        return 1.0 - (double) distance / longer.length() >= MIN_SIMILARITY;
    }

    /** Maiúsculas, sem acento e só letras/dígitos: "Mercado Livre*Eletro" → "MERCADOLIVREELETRO". */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Série a usar nas linhas novas de uma compra: a das parcelas já lançadas, se houver. */
    static UUID seriesFor(List<Future> futures) {
        return futures.stream()
                .map(Future::existing)
                .filter(t -> t != null && t.getSeriesId() != null)
                .map(Transaction::getSeriesId)
                .findFirst()
                .orElseGet(UUID::randomUUID);
    }
}
