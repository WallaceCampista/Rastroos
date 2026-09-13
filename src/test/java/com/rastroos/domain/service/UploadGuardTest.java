package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.rastroos.domain.exception.InvalidUploadException;

/** Validação de upload (§3.2): o que chega do usuário não é confiado pelo nome nem pelo tipo declarado. */
class UploadGuardTest {

    private static final long MAX = 1024;
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    void documentoValido_passa() {
        assertThatCode(() -> document("fatura.pdf", "application/pdf", PDF)).doesNotThrowAnyException();
        assertThatCode(() -> document("print.PNG", "image/png; charset=binary", PNG)).doesNotThrowAnyException();
        assertThatCode(() -> document("foto.jpeg", "image/jpeg", JPEG)).doesNotThrowAnyException();
        assertThatCode(() -> document("C:\\\\pasta\\\\tela.webp", "image/webp", WEBP)).doesNotThrowAnyException();
    }

    @Test
    void arquivoVazio_ouAusente_recusa() {
        assertThatThrownBy(() -> UploadGuard.validate(null, MAX, UploadGuard.DOCUMENT_TYPES, UploadGuard.DOCUMENT_EXTENSIONS))
                .hasMessage("transaction.extract.empty");
        assertThatThrownBy(() -> document("fatura.pdf", "application/pdf", new byte[0]))
                .hasMessage("transaction.extract.empty");
    }

    @Test
    void acimaDoTamanho_recusa() {
        assertThatThrownBy(() -> document("fatura.pdf", "application/pdf", new byte[(int) MAX + 1]))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("transaction.extract.tooLarge");
    }

    @Test
    void tipoOuExtensaoForaDaLista_recusa() {
        assertThatThrownBy(() -> document("fatura.exe", "application/octet-stream", PDF))
                .hasMessage("transaction.extract.badType");
        assertThatThrownBy(() -> document("fatura.exe", "application/pdf", PDF))
                .hasMessage("transaction.extract.badType");
        assertThatThrownBy(() -> document("fatura", "application/pdf", PDF))
                .hasMessage("transaction.extract.badType");
        assertThatThrownBy(() -> document("fatura.pdf", null, PDF))
                .hasMessage("transaction.extract.badType");
    }

    @Test
    void assinaturaQueContradizOTipo_recusa() {
        // PDF disfarçado de foto, numa entrada que só aceita imagem.
        Set<String> images = Set.of("image/jpeg");
        MockMultipartFile disguised = new MockMultipartFile("file", "nota.jpg", "image/jpeg", PDF);

        assertThatThrownBy(() -> UploadGuard.validate(disguised, MAX, images, Set.of("jpg")))
                .hasMessage("transaction.extract.badType");
    }

    @Test
    void extensaoENormalizacaoDeTipo() {
        assertThat(UploadGuard.extensionOf("a/b/c.PDF")).isEqualTo("pdf");
        assertThat(UploadGuard.extensionOf("semponto")).isNull();
        assertThat(UploadGuard.extensionOf("termina.")).isNull();
        assertThat(UploadGuard.extensionOf(null)).isNull();
        assertThat(UploadGuard.normalizeContentType(" Image/PNG ; q=1")).isEqualTo("image/png");
        assertThat(UploadGuard.normalizeContentType(" ")).isNull();
    }

    private static void document(String name, String type, byte[] bytes) {
        UploadGuard.validate(new MockMultipartFile("file", name, type, bytes), MAX,
                UploadGuard.DOCUMENT_TYPES, UploadGuard.DOCUMENT_EXTENSIONS);
    }
}
