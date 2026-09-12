package dev.vitrio.api.asset;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos aceitos nesta versão (spec 003, "Decisão de escopo (MVP)"): JPEG e PNG, os únicos que
 * o {@code ImageIO} padrão do Java lê nativamente. WebP/HEIC ficam pra uma v2. Detecção pelos
 * bytes reais do arquivo (magic bytes) — nunca pela extensão nem pelo {@code Content-Type}
 * declarado no request, que qualquer cliente pode forjar.
 */
enum ImageFormat {
    JPEG("image/jpeg", ".jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG(
            "image/png",
            ".png",
            new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });

    private final String contentType;
    private final String extension;
    private final byte[] magicBytes;

    ImageFormat(String contentType, String extension, byte[] magicBytes) {
        this.contentType = contentType;
        this.extension = extension;
        this.magicBytes = magicBytes;
    }

    String contentType() {
        return contentType;
    }

    String extension() {
        return extension;
    }

    static Optional<ImageFormat> detect(byte[] content) {
        return Arrays.stream(values()).filter(format -> format.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        if (content.length < magicBytes.length) {
            return false;
        }
        for (int i = 0; i < magicBytes.length; i++) {
            if (content[i] != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
