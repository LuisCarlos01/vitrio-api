package dev.vitrio.api.csvimport;

/**
 * Lançada para toda falha de download de imagem na confirmação de importação (spec 006, ADR-0004)
 * — timeout, DNS, bloqueio de SSRF, resposta não-2xx, excesso de redirects. Mensagem única e
 * genérica de propósito: distinguir o motivo real ao cliente abriria um oráculo para mapear a
 * rede interna por tentativa e erro (ADR-0004). O motivo específico de cada ocorrência vai só para
 * o log do servidor, em {@link ImageDownloader}.
 */
public class ImageDownloadException extends RuntimeException {

    public ImageDownloadException() {
        super("could not download image from URL");
    }
}
