package dev.vitrio.api.csvimport;

/** Lançada quando o conteúdo do arquivo CSV não pode ser lido (ex.: cliente encerrou o upload no meio). */
public class CsvReadException extends RuntimeException {

    public CsvReadException(Throwable cause) {
        super("Could not read the uploaded CSV file", cause);
    }
}
