package dev.vitrio.api.csvimport;

/** Lançada quando o arquivo excede 2MB ou 500 linhas — recusado inteiro, antes de validar qualquer linha (spec 006). */
public class CsvFileTooLargeException extends RuntimeException {

    public CsvFileTooLargeException() {
        super("CSV file exceeds the maximum allowed size (2MB) or row count (500)");
    }
}
