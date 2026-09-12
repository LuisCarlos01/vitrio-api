package dev.vitrio.api.csvimport;

import dev.vitrio.api.asset.FileTooLargeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Baixa a imagem de uma URL informada no CSV, com a defesa de SSRF da ADR-0004: só esquemas
 * http/https, host resolvido e revalidado contra {@link SsrfIpBlocklist} antes de cada conexão —
 * inclusive depois de cada redirect HTTP seguido, fechando o TOCTOU de um servidor malicioso que
 * só redireciona pra um IP interno depois do primeiro contato. Timeout de 5s pra conectar e 10s
 * pra ler; leitura limitada a 10MB, abortada no meio do stream se ultrapassar (nunca confia no
 * {@code Content-Length} declarado).
 *
 * <p>Toda falha de rede/DNS/SSRF/resposta não-2xx/excesso de redirect vira {@link
 * ImageDownloadException}, com a mesma mensagem genérica sempre — o motivo real só vai pro log
 * (INFO para falhas comuns, WARN para bloqueio de SSRF, que é o caso mais sensível a auditar).
 * Excesso de tamanho é reportado à parte, como {@link FileTooLargeException} (mesmo erro da spec
 * 003), já que a spec 006 pede a mesma mensagem de tamanho da spec 003 nesse caso — não a
 * mensagem genérica de falha de download.
 */
@Component
public class ImageDownloader {

    private static final Logger log = LoggerFactory.getLogger(ImageDownloader.class);

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final SsrfIpBlocklist blocklist;
    private final HostResolver hostResolver;

    @Autowired
    public ImageDownloader(SsrfIpBlocklist blocklist) {
        this(blocklist, InetAddress::getAllByName);
    }

    // Construtor de teste: permite injetar uma resolução de host falsa, pra exercitar o
    // "redirect pra IP bloqueado" sem depender de DNS real apontando pra uma rede interna.
    ImageDownloader(SsrfIpBlocklist blocklist, HostResolver hostResolver) {
        this.blocklist = blocklist;
        this.hostResolver = hostResolver;
    }

    public byte[] download(String url) {
        try {
            return downloadFollowingRedirects(url, 0);
        } catch (FileTooLargeException | ImageDownloadException e) {
            throw e;
        } catch (RuntimeException e) {
            log.info("Unexpected failure downloading CSV import image from URL: {}", e.toString());
            throw new ImageDownloadException();
        }
    }

    private byte[] downloadFollowingRedirects(String url, int redirectCount) {
        if (redirectCount > MAX_REDIRECTS) {
            log.info("Too many redirects downloading CSV import image, last URL: {}", url);
            throw new ImageDownloadException();
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            log.info("Malformed CSV import image URL: {}", url);
            throw new ImageDownloadException();
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getHost().isBlank()) {
            log.info("Rejected CSV import image URL with unsupported scheme/host: {}", url);
            throw new ImageDownloadException();
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(uri.getHost());
        } catch (UnknownHostException e) {
            log.info("DNS resolution failed for CSV import image host {}: {}", uri.getHost(), e.toString());
            throw new ImageDownloadException();
        }
        if (addresses.length == 0) {
            log.info("DNS resolution returned no addresses for CSV import image host {}", uri.getHost());
            throw new ImageDownloadException();
        }
        for (InetAddress address : addresses) {
            if (blocklist.isBlocked(address)) {
                log.warn(
                        "Blocked SSRF attempt downloading CSV import image: host {} resolved to blocked address {}",
                        uri.getHost(),
                        address);
                throw new ImageDownloadException();
            }
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null || location.isBlank()) {
                    log.info("Redirect without Location header downloading CSV import image from {}", url);
                    throw new ImageDownloadException();
                }
                URI redirectUri = uri.resolve(location);
                return downloadFollowingRedirects(redirectUri.toString(), redirectCount + 1);
            }
            if (status < 200 || status >= 300) {
                log.info("Non-2xx response ({}) downloading CSV import image from {}", status, url);
                throw new ImageDownloadException();
            }
            return readCapped(connection);
        } catch (SocketTimeoutException e) {
            log.info("Timeout downloading CSV import image from {}: {}", url, e.toString());
            throw new ImageDownloadException();
        } catch (IOException e) {
            log.info("I/O error downloading CSV import image from {}: {}", url, e.toString());
            throw new ImageDownloadException();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readCapped(HttpURLConnection connection) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        try (InputStream in = connection.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                // Aborta assim que o corpo ultrapassa 10MB, sem confiar no Content-Length
                // declarado (spec 006, US2 cenário 4).
                if (total > MAX_BYTES) {
                    throw new FileTooLargeException();
                }
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
