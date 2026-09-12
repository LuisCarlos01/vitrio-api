package dev.vitrio.api.csvimport;

import java.net.Inet6Address;
import java.net.InetAddress;
import org.springframework.stereotype.Component;

/**
 * Blocklist de IP usada pela defesa de SSRF da importação de CSV (spec 006, ADR-0004): nunca uma
 * allowlist de domínios — qualquer host público é aceito, só o IP resolvido é checado contra
 * faixas privadas/loopback/link-local/metadados de nuvem. Componente isolado (em vez de embutido
 * direto em {@link ImageDownloader}) só para poder ser testado sozinho, sem rede, e para poder ser
 * substituído nos testes de integração que sobem um servidor HTTP local em loopback (ver
 * {@code docs/adr/0004-ssrf-defense-for-server-side-url-fetches.md}).
 */
@Component
public class SsrfIpBlocklist {

    public boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress() // cobre 169.254.0.0/16, inclusive metadados de nuvem (169.254.169.254)
                || address.isSiteLocalAddress() // cobre 10/8, 172.16/12, 192.168/16 (IPv4)
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        return address instanceof Inet6Address && isUniqueLocalIpv6(address.getAddress());
    }

    // fc00::/7 (IPv6 unique-local) — os 7 bits mais altos do primeiro byte são 1111110, ou seja
    // byte & 0xFE == 0xFC. Não coberto por isSiteLocalAddress(), que só trata site-local antigo
    // (fec0::/10, já deprecado) e as faixas privadas de IPv4.
    private boolean isUniqueLocalIpv6(byte[] address) {
        return (address[0] & 0xFE) == 0xFC;
    }
}
