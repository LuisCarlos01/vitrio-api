package dev.vitrio.api.asset;

import java.util.Arrays;

/** Bytes mínimos pra exercitar a detecção de formato por magic bytes (spec 003) — não são imagens decodificáveis de verdade, só o cabeçalho que {@link ImageFormat#detect} inspeciona. */
final class ImageFixtures {

    static final byte[] JPEG_BYTES = concat(
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, new byte[100]);

    static final byte[] PNG_BYTES =
            concat(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, new byte[100]);

    static final byte[] PLAIN_TEXT_BYTES = "not really an image".getBytes();

    private ImageFixtures() {}

    static byte[] jpegOfSize(int totalBytes) {
        byte[] content = new byte[totalBytes];
        byte[] magic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
