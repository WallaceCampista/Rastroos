package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.rastroos.config.ExtractionProperties;
import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;
import com.rastroos.domain.exception.InvalidUploadException;
import com.rastroos.domain.repository.AccountRepository;
import com.rastroos.web.dto.ExtractedExpense;

@ExtendWith(MockitoExtension.class)
class ExpenseExtractionServiceTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34};   // %PDF-1.4
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Mock private AccountRepository accountsRepo;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:00:00Z"), ZoneOffset.UTC);
    private final UUID alice = UUID.randomUUID();
    private ExtractionProperties props;
    private ExpenseExtractionService service;

    @BeforeEach
    void init() {
        props = new ExtractionProperties();
        service = new ExpenseExtractionService(props, accountsRepo, clock);
    }

    @Test
    void extractDocumentUsaNomeDoArquivoComoDescricaoEMarcaDemo() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fatura_nubank.pdf", "application/pdf", PDF);

        ExtractedExpense ex = service.extract(alice, file, ExpenseExtractionSource.DOCUMENT);

        assertThat(ex.description()).isEqualTo("fatura nubank");
        assertThat(ex.dueDate()).isEqualTo(LocalDate.of(2026, 5, 18));
        assertThat(ex.amount()).isNull();       // não inventa valor monetário
        assertThat(ex.accountId()).isNull();
        assertThat(ex.demo()).isTrue();
    }

    @Test
    void extractReceiptRetornaSugestaoGenericaEMarcaDemo() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", "image/jpeg", JPEG);

        ExtractedExpense ex = service.extract(alice, file, ExpenseExtractionSource.RECEIPT);

        assertThat(ex.description()).isEqualTo("Compra no cartão");
        assertThat(ex.demo()).isTrue();
        assertThat(ex.amount()).isNull();
    }

    @Test
    void extractRejeitaArquivoVazio() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.extract(alice, empty, ExpenseExtractionSource.DOCUMENT))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("transaction.extract.empty");
    }

    @Test
    void extractRejeitaArquivoAcimaDoLimite() {
        props.setMaxFileSizeBytes(4);
        MockMultipartFile big = new MockMultipartFile("file", "x.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.extract(alice, big, ExpenseExtractionSource.DOCUMENT))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("transaction.extract.tooLarge");
    }

    @Test
    void extractRejeitaTipoNaoSuportado() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notas.txt", "text/plain", "olá".getBytes());

        assertThatThrownBy(() -> service.extract(alice, txt, ExpenseExtractionSource.DOCUMENT))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("transaction.extract.badType");
    }

    @Test
    void extractRejeitaAssinaturaQueContradizOTipo() {
        // Diz ser PNG (permitido em RECEIPT), mas o conteúdo é um PDF disfarçado.
        MockMultipartFile fake = new MockMultipartFile("file", "nota.png", "image/png", PDF);

        assertThatThrownBy(() -> service.extract(alice, fake, ExpenseExtractionSource.RECEIPT))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("transaction.extract.badType");
    }

    @Test
    void matchAccountByLast4CasaCartaoDoUsuario() {
        Account card = card("Nubank", "1234");
        Account outroCartao = card("Inter", "9999");
        Account boleto = new Account();
        boleto.setId(UUID.randomUUID());
        boleto.setKind(AccountKind.BILL);
        boleto.setLast4("1234"); // não é cartão → ignorado
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(boleto, outroCartao, card));

        assertThat(service.matchAccountByLast4(alice, "1234")).isEqualTo(card.getId());
    }

    @Test
    void matchAccountByLast4RetornaNullQuandoNaoBate() {
        when(accountsRepo.findAllByUserIdOrderByNameAsc(alice))
                .thenReturn(List.of(card("Nubank", "1234")));

        assertThat(service.matchAccountByLast4(alice, "0000")).isNull();
    }

    @Test
    void matchAccountByLast4RetornaNullSemConsultarQuandoLast4Ausente() {
        assertThat(service.matchAccountByLast4(alice, null)).isNull();
        assertThat(service.matchAccountByLast4(alice, "  ")).isNull();
        // accountsRepo nunca é consultado (sem stub) → verifica o short-circuit.
    }

    private Account card(String name, String last4) {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        a.setUserId(alice);
        a.setName(name);
        a.setKind(AccountKind.CARD);
        a.setLast4(last4);
        return a;
    }
}
