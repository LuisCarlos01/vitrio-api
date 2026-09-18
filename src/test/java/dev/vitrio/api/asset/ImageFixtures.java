package dev.vitrio.api.asset;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Imagens de verdade, decodificáveis por {@code ImageIO} (spec 013 exige isso — {@link
 * ImageOptimizer} chama {@code ImageIO.read} de verdade, diferente da validação de formato por
 * magic bytes, que não precisa de conteúdo decodificável).
 */
final class ImageFixtures {

    static final byte[] JPEG_BYTES = jpegOfDimensions(50, 50);
    static final byte[] PNG_BYTES = pngOfDimensions(50, 50);

    static final byte[] PLAIN_TEXT_BYTES = "not really an image".getBytes();

    private ImageFixtures() {}

    static byte[] jpegOfDimensions(int width, int height) {
        return encode("jpg", width, height, BufferedImage.TYPE_INT_RGB);
    }

    static byte[] pngOfDimensions(int width, int height) {
        return encode("png", width, height, BufferedImage.TYPE_INT_ARGB);
    }

    /** Bytes com o magic byte de JPEG, mas não decodificáveis — só pra exercitar o limite de tamanho, checado antes de qualquer leitura de conteúdo real. */
    static byte[] jpegOfSize(int totalBytes) {
        byte[] content = new byte[totalBytes];
        byte[] magic = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }

    /**
     * JPEG de verdade, decodificável, com ruído aleatório pixel a pixel — comprime muito pior
     * que uma foto real (que tem áreas lisas/redundância), então já fica na casa de poucos MB em
     * dimensões bem menores que uma foto de celular de verdade (o cenário real da issue #22, uma
     * foto de 8-9MB). Usado só pra provar que o processamento não falha numa imagem grande, sem
     * pagar o custo de gerar dezenas de MB no teste.
     */
    static byte[] noisyJpegOfDimensions(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encode(String formatName, int width, int height, int imageType) {
        BufferedImage image = new BufferedImage(width, height, imageType);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        // Um retângulo semitransparente no canto — só usado pelos testes de PNG que verificam
        // que a resolução preserva o canal alfa (spec 013, cenário 3).
        graphics.setColor(new Color(255, 0, 0, 128));
        graphics.fillRect(0, 0, Math.max(1, width / 4), Math.max(1, height / 4));
        graphics.dispose();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, formatName, output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
