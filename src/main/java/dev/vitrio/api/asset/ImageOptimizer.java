package dev.vitrio.api.asset;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redimensiona e (só para JPEG) recomprime a imagem enviada pra um asset (spec 013) — chamado
 * entre a validação de formato e o upload em {@link AssetService}, nunca salva o original bruto.
 * PNG só é redimensionado quando acima do limite: {@code ImageIO}/Thumbnailator não têm knob de
 * qualidade pra um formato lossless, e converter pra JPEG derrubaria a transparência.
 *
 * <p>O Thumbnailator constrói a partir de um {@code InputStream} dos bytes brutos, não de um
 * {@link BufferedImage} já decodificado — é o que preserva a correção automática de orientação
 * EXIF (metadado perdido assim que a imagem já foi decodificada), relevante justamente pro caso
 * que motivou a spec 013: foto de produto tirada direto do celular.
 */
@Component
class ImageOptimizer {

    private final int maxDimensionPx;
    private final float jpegQuality;

    ImageOptimizer(
            @Value("${vitrio.asset.max-dimension-px}") int maxDimensionPx,
            @Value("${vitrio.asset.jpeg-quality}") float jpegQuality) {
        this.maxDimensionPx = maxDimensionPx;
        this.jpegQuality = jpegQuality;
    }

    byte[] optimize(byte[] content, ImageFormat format) {
        try {
            // Só pra decidir se precisa redimensionar — a construção do Thumbnails abaixo parte
            // de novo dos bytes brutos (não deste BufferedImage), pra preservar a orientação EXIF
            // (aplicada automaticamente pelo Thumbnailator só quando a entrada é File/InputStream,
            // nunca quando já é um BufferedImage decodificado sem metadado).
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(content));
            if (probe == null) {
                // magic bytes bateram um formato aceito, mas o codec do ImageIO não decodifica o
                // conteúdo de verdade (ex.: JPEG CMYK) — mesmo tratamento de "não suportado" da
                // validação de formato, não um NPE genérico de framework.
                throw new UnsupportedImageFormatException();
            }
            boolean needsResize = Math.max(probe.getWidth(), probe.getHeight()) > maxDimensionPx;

            // PNG já dentro do limite: nada a fazer, sem knob de qualidade pra ganhar recomprimindo.
            if (format == ImageFormat.PNG && !needsResize) {
                return content;
            }

            Thumbnails.Builder<? extends InputStream> builder = Thumbnails.of(new ByteArrayInputStream(content));
            if (needsResize) {
                // Encaixa dentro de um quadrado maxDimensionPx x maxDimensionPx preservando a
                // proporção — o lado maior vira exatamente maxDimensionPx.
                builder.size(maxDimensionPx, maxDimensionPx);
            } else {
                // JPEG já dentro do limite: mantém dimensão original, só recomprime a qualidade.
                builder.scale(1.0);
            }
            if (format == ImageFormat.JPEG) {
                builder.outputFormat("jpg").outputQuality(jpegQuality);
            } else {
                builder.outputFormat("png");
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            builder.toOutputStream(output);
            return output.toByteArray();
        } catch (IOException e) {
            // Só falha num I/O de verdade — o formato já foi validado pelos magic bytes antes de
            // chegar aqui (spec 013, cenário 4: nenhum JPEG/PNG válido falha por causa disso).
            throw new AssetReadException(e);
        }
    }
}
