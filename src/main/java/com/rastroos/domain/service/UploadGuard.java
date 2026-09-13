package com.rastroos.domain.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

import com.rastroos.domain.exception.InvalidUploadException;

/**
 * Validação de arquivo enviado pelo usuário (§3.2): não-vazio, tamanho máximo,
 * tipo de conteúdo e extensão numa lista fechada, e assinatura (magic bytes)
 * que não contradiga o tipo declarado. Compartilhada pela leitura de notinha e
 * pela leitura de fatura.
 *
 * <p>Erros saem como {@link InvalidUploadException} com chave i18n.
 */
public final class UploadGuard {

    /** PDF e imagens que o modelo de visão lê. */
    public static final Set<String> DOCUMENT_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");
    public static final Set<String> DOCUMENT_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "webp");

    private UploadGuard() {
    }

    public static void validate(MultipartFile file, long maxBytes,
                                Set<String> allowedTypes, Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("transaction.extract.empty");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException("transaction.extract.tooLarge");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !allowedExtensions.contains(ext)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
        // Defesa em profundidade: assinatura do arquivo não pode contradizer o
        // tipo declarado (ex.: PDF disfarçado de imagem). Formatos que não
        // sabemos "farejar" (heic/heif) passam pelo tipo+extensão.
        String sniffed = sniff(file);
        if (sniffed != null && !allowedTypes.contains(sniffed)) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semi = contentType.indexOf(';');
        String base = (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
        return base.isEmpty() ? null : base;
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /** Detecta o tipo pelos primeiros bytes; {@code null} se não reconhecer. */
    static String sniff(MultipartFile file) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new InvalidUploadException("transaction.extract.badType");
        }
        if (read >= 4 && head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46) {
            return "application/pdf"; // %PDF
        }
        if (read >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
            return "image/png";
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
