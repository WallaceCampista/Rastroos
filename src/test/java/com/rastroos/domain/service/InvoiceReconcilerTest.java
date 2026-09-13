package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.rastroos.domain.entity.Transaction;
import com.rastroos.domain.service.InvoiceReconciler.Future;
import com.rastroos.domain.service.InvoiceReconciler.Line;
import com.rastroos.domain.service.InvoiceReconciler.Status;

/**
 * O que se protege aqui é a promessa da importação: anexar a mesma fatura de
 * novo, ou a fatura do mês seguinte, nunca duplica lançamento — e duas compras
 * iguais de verdade continuam sendo duas.
 */
class InvoiceReconcilerTest {

    private static final LocalDate OCT = LocalDate.of(2026, 10, 10);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 10);

    private final List<Transaction> db = new ArrayList<>();
    private long clock = 0;

    @Test
    void primeiraImportacao_tudoNovo_eParcelaPlanejaAsSeguintes() {
        List<Line> lines = List.of(oneOff("UBER *TRIP", 1590), installment("LOJA X", 10000, 3, 10));

        InvoiceReconciler r = InvoiceReconciler.reconcile(lines, OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
        assertThat(r.status(1)).isEqualTo(Status.NEW);
        List<Future> futures = r.futures(1);
        assertThat(futures).extracting(Future::number)
                .containsExactly((short) 4, (short) 5, (short) 6, (short) 7, (short) 8, (short) 9, (short) 10);
        assertThat(futures).allMatch(Future::toCreate);
        assertThat(futures.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(futures.get(6).dueDate()).isEqualTo(LocalDate.of(2027, 5, 10));
        assertThat(r.futures(0)).isEmpty();
    }

    @Test
    void mesmaFaturaDeNovo_nadaENovo() {
        importOctober();

        InvoiceReconciler r = InvoiceReconciler.reconcile(octoberLines(), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.EXISTING);
        assertThat(r.status(1)).isEqualTo(Status.EXISTING);
    }

    @Test
    void faturaDoMesSeguinte_parcelaJaProjetadaNaoDuplica_eCompraNovaEntra() {
        importOctober();
        List<Line> november = List.of(installment("LOJA X", 10000, 4, 10), oneOff("IFOOD", 4590));

        InvoiceReconciler r = InvoiceReconciler.reconcile(november, NOV, db);

        assertThat(r.status(0)).isEqualTo(Status.EXISTING);
        assertThat(r.match(0).getInstallmentCurrent()).isEqualTo((short) 4);
        assertThat(r.status(1)).isEqualTo(Status.NEW);
    }

    @Test
    void faturaDoMesSeguinte_leituraComVariacaoNaDescricao_aindaCasa() {
        existing("MERCADOLIVRE*ELETRO", 25000, NOV, (short) 2, (short) 5, false);

        InvoiceReconciler r = InvoiceReconciler.reconcile(
                List.of(installment("Mercado Livre Eletro", 25000, 2, 5)), NOV, db);

        assertThat(r.status(0)).isEqualTo(Status.EXISTING);
    }

    @Test
    void compraDeOutroMes_naoCasa() {
        existing("UBER *TRIP", 1590, LocalDate.of(2026, 9, 10), null, null, false);

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(oneOff("UBER *TRIP", 1590)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
    }

    @Test
    void duasComprasIguaisNaFatura_precisamDeDoisLancamentos() {
        existing("CAFETERIA", 500, OCT, null, null, false);
        List<Line> lines = List.of(oneOff("CAFETERIA", 500), oneOff("CAFETERIA", 500));

        InvoiceReconciler r = InvoiceReconciler.reconcile(lines, OCT, db);

        assertThat(List.of(r.status(0), r.status(1))).containsExactlyInAnyOrder(Status.EXISTING, Status.NEW);
    }

    @Test
    void parcelaComCentavosDeDiferenca_ajustaSeAindaEmAberto() {
        existing("LOJA X", 3333, OCT, (short) 10, (short) 10, false);

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(installment("LOJA X", 3337, 10, 10)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.ADJUST);
        assertThat(r.match(0).getAmountCents()).isEqualTo(3333);
    }

    @Test
    void parcelaComCentavosDeDiferenca_jaPaga_naoMexe() {
        existing("LOJA X", 3333, OCT, (short) 10, (short) 10, true);

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(installment("LOJA X", 3337, 10, 10)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.EXISTING);
    }

    @Test
    void compraAVistaComValorDiferente_eOutraCompra() {
        existing("UBER *TRIP", 1590, OCT, null, null, false);

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(oneOff("UBER *TRIP", 1690)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
    }

    @Test
    void lancadoAMaoComOutroNome_viraPossivelDuplicado() {
        existing("Geladeira", 45000, OCT, (short) 3, (short) 10, false);
        existing("Almoço", 4590, OCT, null, null, false);
        List<Line> lines = List.of(installment("MAGAZINE LUIZA", 45000, 3, 10), oneOff("RESTAURANTE SABOR", 4590));

        InvoiceReconciler r = InvoiceReconciler.reconcile(lines, OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.POSSIBLE_DUPLICATE);
        assertThat(r.match(0).getDescription()).isEqualTo("Geladeira");
        assertThat(r.status(1)).isEqualTo(Status.POSSIBLE_DUPLICATE);
    }

    @Test
    void faturaAnteriorImportadaDepois_reaproveitaAsParcelasEASerie() {
        UUID series = UUID.randomUUID();
        for (int k = 4; k <= 10; k++) {
            Transaction t = existing("LOJA X", 10000, OCT.plusMonths(k - 3), (short) k, (short) 10, false);
            t.setSeriesId(series);
        }

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(installment("LOJA X", 10000, 3, 10)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
        List<Future> futures = r.futures(0);
        assertThat(futures).hasSize(7).noneMatch(Future::toCreate);
        assertThat(InvoiceReconciler.seriesFor(futures)).isEqualTo(series);
    }

    @Test
    void parExatoNaoPerdeOLancamentoParaUmParAproximado() {
        existing("LOJA X", 3337, OCT, (short) 4, (short) 10, false);
        existing("LOJA X", 3333, OCT, (short) 4, (short) 10, false);
        List<Line> lines = List.of(installment("LOJA X", 3333, 4, 10), installment("LOJA X", 3337, 4, 10));

        InvoiceReconciler r = InvoiceReconciler.reconcile(lines, OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.EXISTING);
        assertThat(r.status(1)).isEqualTo(Status.EXISTING);
        assertThat(r.match(0).getAmountCents()).isEqualTo(3333);
        assertThat(r.match(1).getAmountCents()).isEqualTo(3337);
    }

    @Test
    void duasParceladasIdenticas_naoDividemAMesmaParcelaFutura() {
        existing("LOJA X", 10000, NOV, (short) 4, (short) 10, false);
        List<Line> lines = List.of(installment("LOJA X", 10000, 3, 10), installment("LOJA X", 10000, 3, 10));

        InvoiceReconciler r = InvoiceReconciler.reconcile(lines, OCT, db);

        long first = r.futures(0).stream().filter(Future::toCreate).count();
        long second = r.futures(1).stream().filter(Future::toCreate).count();
        assertThat(first).isEqualTo(6);
        assertThat(second).isEqualTo(7);
    }

    @Test
    void ultimaParcela_naoPlanejaNadaAFrente() {
        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(installment("LOJA X", 10000, 10, 10)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
        assertThat(r.futures(0)).isEmpty();
    }

    @Test
    void parcelaDoMesNaoCasaComCompraAVista() {
        existing("LOJA X", 10000, OCT, null, null, false);

        InvoiceReconciler r = InvoiceReconciler.reconcile(List.of(installment("LOJA X", 10000, 3, 10)), OCT, db);

        assertThat(r.status(0)).isEqualTo(Status.NEW);
    }

    @Test
    void descricoesEquivalentes() {
        assertThat(InvoiceReconciler.similar("Mercado Livre*Eletro", "MERCADOLIVRE ELETRO")).isTrue();
        assertThat(InvoiceReconciler.similar("PADARIA SÃO JOÃO", "Padaria Sao Joao")).isTrue();
        // O banco trunca o nome do estabelecimento.
        assertThat(InvoiceReconciler.similar("NETFLIX.COM", "NETFLIX")).isTrue();
        // Uma letra trocada pela leitura.
        assertThat(InvoiceReconciler.similar("DROGARIA PACHECO", "DROGARIA PACHEC0")).isTrue();
        assertThat(InvoiceReconciler.similar("Geladeira", "MAGAZINE LUIZA")).isFalse();
        assertThat(InvoiceReconciler.similar("", "UBER")).isFalse();
        assertThat(InvoiceReconciler.similar("99", "99 TAXI")).isFalse();
    }

    @Test
    void toleranciaDeArredondamento() {
        assertThat(InvoiceReconciler.withinTolerance(3333, 3337)).isTrue();
        assertThat(InvoiceReconciler.withinTolerance(300, 305)).isTrue();
        assertThat(InvoiceReconciler.withinTolerance(300, 306)).isFalse();
        assertThat(InvoiceReconciler.withinTolerance(100000, 101000)).isTrue();
        assertThat(InvoiceReconciler.withinTolerance(100000, 101001)).isFalse();
    }

    // ── Apoio ────────────────────────────────────────────────────────────

    private List<Line> octoberLines() {
        return List.of(oneOff("UBER *TRIP", 1590), installment("LOJA X", 10000, 3, 10));
    }

    /** Grava no "banco" o que a importação de outubro criaria. */
    private void importOctober() {
        existing("UBER *TRIP", 1590, OCT, null, null, false);
        UUID series = UUID.randomUUID();
        for (int k = 3; k <= 10; k++) {
            existing("LOJA X", 10000, OCT.plusMonths(k - 3), (short) k, (short) 10, false).setSeriesId(series);
        }
    }

    private Transaction existing(String description, long cents, LocalDate due,
                                 Short current, Short total, boolean paid) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setDescription(description);
        t.setAmountCents(cents);
        t.setDueDate(due);
        t.setInstallmentCurrent(current);
        t.setInstallmentTotal(total);
        t.setPaid(paid);
        t.setCreatedAt(Instant.ofEpochSecond(clock++));
        db.add(t);
        return t;
    }

    private static Line oneOff(String description, long cents) {
        return new Line(description, cents, null, null);
    }

    private static Line installment(String description, long cents, int current, int total) {
        return new Line(description, cents, (short) current, (short) total);
    }
}
