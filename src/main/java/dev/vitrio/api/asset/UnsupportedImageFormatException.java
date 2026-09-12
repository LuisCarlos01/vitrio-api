package dev.vitrio.api.asset;

/**
 * Lançada quando o conteúdo real do arquivo (magic bytes) não corresponde a nenhum formato
 * aceito — mesmo que a extensão ou o {@code Content-Type} declarado pareçam válidos (spec 003,
 * US1 cenário 2).
 */
public class UnsupportedImageFormatException extends RuntimeException {

    public UnsupportedImageFormatException() {
        super("Unsupported image format");
    }
}
