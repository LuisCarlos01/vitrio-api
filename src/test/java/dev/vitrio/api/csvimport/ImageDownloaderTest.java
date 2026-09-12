package dev.vitrio.api.csvimport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.vitrio.api.asset.FileTooLargeException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link ImageDownloader} contra um servidor HTTP local de verdade ({@code
 * com.sun.net.httpserver.HttpServer}, módulo {@code jdk.httpserver} — sem dependência de teste
 * nova). O servidor de teste necessariamente sobe em loopback, o que a {@link SsrfIpBlocklist} de
 * produção rejeitaria; por isso a maioria dos testes aqui usa uma blocklist "permite tudo"
 * (subclasse anônima), já que a lógica real da blocklist é provada isoladamente, sem rede, em
 * {@link SsrfIpBlocklistTest}. O teste de "redirect para IP bloqueado" é o único que usa uma
 * blocklist real (bloqueando só o alvo do redirect) e um {@link ImageDownloader.HostResolver}
 * falso (o construtor de pacote existe só para isso).
 */
class ImageDownloaderTest {

    private static final SsrfIpBlocklist ALLOW_ALL = new SsrfIpBlocklist() {
        @Override
        public boolean isBlocked(InetAddress address) {
            return false;
        }
    };

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void downloadsFixedBytesSuccessfully() throws IOException {
        byte[] expected = new byte[] {1, 2, 3, 4, 5};
        server = startServer("/image", exchange -> respond(exchange, 200, expected));

        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        byte[] downloaded = downloader.download(urlFor("/image"));

        assertArrayEquals(expected, downloaded);
    }

    @Test
    void followsARedirectToAnotherServer() throws IOException {
        byte[] expected = new byte[] {9, 8, 7};
        HttpServer target = startServer("/final", exchange -> respond(exchange, 200, expected));
        try {
            String targetUrl = "http://" + target.getAddress().getHostString() + ":" + target.getAddress().getPort() + "/final";
            server = startServer("/redirect", exchange -> {
                exchange.getResponseHeaders().add("Location", targetUrl);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });

            ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
            byte[] downloaded = downloader.download(urlFor("/redirect"));

            assertArrayEquals(expected, downloaded);
        } finally {
            target.stop(0);
        }
    }

    @Test
    void nonTwoHundredResponseIsAGenericDownloadFailure() throws IOException {
        server = startServer("/missing", exchange -> respond(exchange, 404, new byte[0]));

        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        assertThrows(ImageDownloadException.class, () -> downloader.download(urlFor("/missing")));
    }

    @Test
    void tooManyRedirectsIsAGenericDownloadFailure() throws IOException {
        server = startServer("/loop", exchange -> {
            String selfUrl = urlFor("/loop");
            exchange.getResponseHeaders().add("Location", selfUrl);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        assertThrows(ImageDownloadException.class, () -> downloader.download(urlFor("/loop")));
    }

    @Test
    void bodyLargerThanTenMegabytesAbortsMidStream() throws IOException {
        // A API do HttpServer sempre recalcula o Content-Length real a partir do que é
        // efetivamente escrito, então não dá pra simular um cabeçalho mentiroso via HTTP de
        // verdade aqui — o que este teste prova é o que importa na prática: o downloader nunca lê
        // o cabeçalho Content-Length em código nenhum (ver ImageDownloader.readCapped), só conta
        // bytes realmente lidos do stream, então o resultado é o mesmo esteja o cabeçalho certo
        // ou mentiroso (spec 006, US2 cenário 4).
        int elevenMegabytes = 11 * 1024 * 1024;
        server = startServer("/huge", exchange -> {
            exchange.sendResponseHeaders(200, elevenMegabytes);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(new byte[elevenMegabytes]);
            }
        });

        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        assertThrows(FileTooLargeException.class, () -> downloader.download(urlFor("/huge")));
    }

    @Test
    void redirectToAHostThatResolvesToABlockedIpIsNeverConnectedTo() throws IOException {
        // O servidor local em loopback é permitido (blocklist só bloqueia o IP-alvo do redirect,
        // 203.0.113.10) — simula um host de verdade cujo DNS aponta pra rede interna, sem
        // depender de DNS real.
        SsrfIpBlocklist blocklist = new SsrfIpBlocklist() {
            @Override
            public boolean isBlocked(InetAddress address) {
                return "203.0.113.10".equals(address.getHostAddress());
            }
        };
        ImageDownloader.HostResolver resolver = host -> {
            if ("blocked.internal.example".equals(host)) {
                return new InetAddress[] {InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 10})};
            }
            return InetAddress.getAllByName(host);
        };

        server = startServer("/redirect-to-blocked", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://blocked.internal.example/image.jpg");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        ImageDownloader downloader = new ImageDownloader(blocklist, resolver);
        assertThrows(ImageDownloadException.class, () -> downloader.download(urlFor("/redirect-to-blocked")));
    }

    @Test
    void unsupportedSchemeIsRejectedWithoutConnecting() {
        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        assertThrows(ImageDownloadException.class, () -> downloader.download("ftp://example.com/image.jpg"));
    }

    @Test
    void unresolvableHostIsAGenericDownloadFailure() {
        ImageDownloader.HostResolver alwaysFails = host -> {
            throw new UnknownHostException(host);
        };
        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL, alwaysFails);
        assertThrows(ImageDownloadException.class, () -> downloader.download("http://does-not-resolve.example/image.jpg"));
    }

    @Test
    void readTimeoutIsAGenericDownloadFailure() throws IOException {
        server = startServer("/slow", exchange -> {
            // Escreve o cabeçalho e nunca escreve o corpo dentro dos 10s de read timeout —
            // simula uma origem que trava no meio da resposta (spec 006, US2 cenário 5).
            exchange.sendResponseHeaders(200, 5);
            try {
                Thread.sleep(11_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(new byte[] {1, 2, 3, 4, 5});
            } catch (IOException ignored) {
                // Cliente já desistiu (timeout) antes de conseguirmos escrever — esperado.
            }
        });

        ImageDownloader downloader = new ImageDownloader(ALLOW_ALL);
        assertThrows(ImageDownloadException.class, () -> downloader.download(urlFor("/slow")));
    }

    private HttpServer startServer(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext(path, handler);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        return httpServer;
    }

    private String urlFor(String path) {
        return urlFor(server, path);
    }

    private String urlFor(HttpServer target, String path) {
        return "http://" + target.getAddress().getHostString() + ":" + target.getAddress().getPort() + path;
    }

    private void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
