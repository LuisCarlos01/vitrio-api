package dev.vitrio.api.asset;

/** Lançada quando o conteúdo do arquivo multipart não pode ser lido (ex.: cliente encerrou o upload no meio). */
public class AssetReadException extends RuntimeException {

    public AssetReadException(Throwable cause) {
        super("Could not read the uploaded file", cause);
    }
}
