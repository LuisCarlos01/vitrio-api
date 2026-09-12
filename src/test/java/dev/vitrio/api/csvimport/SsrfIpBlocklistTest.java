package dev.vitrio.api.csvimport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro (sem contexto Spring, sem rede) de {@link SsrfIpBlocklist} — todos os
 * endereços são construídos via {@link InetAddress#getByAddress(byte[])} com bytes literais, o
 * que nunca dispara resolução de DNS de verdade (spec 006, ADR-0004).
 */
class SsrfIpBlocklistTest {

    private final SsrfIpBlocklist blocklist = new SsrfIpBlocklist();

    @Test
    void blocksIpv4Loopback() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})));
    }

    @Test
    void blocksIpv6Loopback() throws UnknownHostException {
        byte[] loopback = new byte[16];
        loopback[15] = 1;
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(loopback)));
    }

    @Test
    void blocksCloudMetadataLinkLocalAddress() throws UnknownHostException {
        // 169.254.169.254 — metadados de nuvem (AWS/GCP/Azure), dentro de 169.254.0.0/16.
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {(byte) 169, (byte) 254, (byte) 169, (byte) 254})));
    }

    @Test
    void blocksPrivateIpv4TenSlashEight() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {10, 0, 0, 1})));
    }

    @Test
    void blocksPrivateIpv4OneSevenTwoSlashTwelve() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {(byte) 172, 16, 0, 1})));
    }

    @Test
    void blocksPrivateIpv4OneNineTwoSlashSixteen() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 0, 1})));
    }

    @Test
    void blocksIpv4AnyLocalAddress() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {0, 0, 0, 0})));
    }

    @Test
    void blocksIpv4MulticastAddress() throws UnknownHostException {
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {(byte) 224, 0, 0, 1})));
    }

    @Test
    void blocksIpv6UniqueLocalAddress() throws UnknownHostException {
        // fc00::/7 — bytes começando em 0xfc ou 0xfd.
        byte[] uniqueLocal = new byte[16];
        uniqueLocal[0] = (byte) 0xfd;
        uniqueLocal[15] = 1;
        assertTrue(blocklist.isBlocked(InetAddress.getByAddress(uniqueLocal)));
    }

    @Test
    void allowsPublicIpv4Addresses() throws UnknownHostException {
        assertFalse(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {8, 8, 8, 8})));
        assertFalse(blocklist.isBlocked(InetAddress.getByAddress(new byte[] {1, 1, 1, 1})));
    }

    @Test
    void allowsPublicIpv6Address() throws UnknownHostException {
        // 2001:4860:4860::8888 (Google Public DNS) — fora de fc00::/7 e de qualquer outra faixa
        // bloqueada.
        byte[] publicIpv6 = new byte[] {
            0x20, 0x01, 0x48, 0x60, 0x48, 0x60, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x88, (byte) 0x88
        };
        assertFalse(blocklist.isBlocked(InetAddress.getByAddress(publicIpv6)));
    }
}
