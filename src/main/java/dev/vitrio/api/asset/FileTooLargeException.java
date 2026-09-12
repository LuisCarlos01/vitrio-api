package dev.vitrio.api.asset;

/** Lançada quando o arquivo excede o limite de 10MB, antes de qualquer upload pro S3 (spec 003, US1 cenário 3). */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException() {
        super("File exceeds the maximum allowed size of 10MB");
    }
}
