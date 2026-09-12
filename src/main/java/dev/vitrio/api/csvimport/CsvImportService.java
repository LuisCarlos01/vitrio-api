package dev.vitrio.api.csvimport;

import dev.vitrio.api.asset.AssetResponse;
import dev.vitrio.api.asset.AssetService;
import dev.vitrio.api.asset.FileTooLargeException;
import dev.vitrio.api.asset.UnsupportedImageFormatException;
import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.product.CreateProductRequest;
import dev.vitrio.api.product.DuplicateSkuException;
import dev.vitrio.api.product.ProductLimitExceededException;
import dev.vitrio.api.product.ProductRepository;
import dev.vitrio.api.product.ProductResponse;
import dev.vitrio.api.product.ProductService;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvImportService {

    // Proteção contra arquivo abusivo (spec 006) — independente do limite de 50 produtos por
    // catálogo, que é checado linha a linha.
    private static final int MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ROWS = 500;

    // Mesmo limite de dev.vitrio.api.product.ProductService — CSV não é exceção à regra da
    // spec 004, checado aqui também linha a linha (existentes + válidas até agora no arquivo).
    private static final int MAX_PRODUCTS_PER_CATALOG = 50;

    private final CatalogRepository catalogRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final AssetService assetService;
    private final ImageDownloader imageDownloader;

    public CsvImportService(
            CatalogRepository catalogRepository,
            ProductRepository productRepository,
            ProductService productService,
            AssetService assetService,
            ImageDownloader imageDownloader) {
        this.catalogRepository = catalogRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.assetService = assetService;
        this.imageDownloader = imageDownloader;
    }

    @Transactional(readOnly = true)
    public List<CsvImportRowResult> validate(UUID ownerId, UUID catalogId, MultipartFile file) {
        requireOwnedCatalog(ownerId, catalogId);
        return validateStructure(catalogId, file);
    }

    /**
     * Confirmação da importação (spec 006, US2): reroda a mesma validação estrutural da prévia e,
     * pra cada linha que passar, baixa a imagem (ADR-0004), envia pro S3 (spec 003) e cria o
     * {@code Product} (spec 004). Nunca tudo-ou-nada — cada linha é reportada individualmente,
     * mesmo quando outras falham. Deliberadamente sem {@code @Transactional} neste nível: cada
     * linha pode envolver um download de rede de até 10s (timeout), e {@link ProductService#create}
     * / {@link AssetService#upload(UUID, byte[])} já são transacionais por conta própria — uma
     * transação única cobrindo até 50 downloads sequenciais seguraria uma conexão de banco por
     * tempo desproporcional.
     */
    public List<CsvImportConfirmRowResult> confirm(UUID ownerId, UUID catalogId, MultipartFile file) {
        requireOwnedCatalog(ownerId, catalogId);
        List<CsvImportRowResult> structuralResults = validateStructure(catalogId, file);

        List<CsvImportConfirmRowResult> results = new ArrayList<>();
        for (CsvImportRowResult row : structuralResults) {
            results.add(confirmRow(ownerId, catalogId, row));
        }
        return results;
    }

    private CsvImportConfirmRowResult confirmRow(UUID ownerId, UUID catalogId, CsvImportRowResult row) {
        if (!row.isValid()) {
            return CsvImportConfirmRowResult.from(row, null, row.errors());
        }

        try {
            byte[] imageContent = imageDownloader.download(row.imageUrl());
            AssetResponse asset = assetService.upload(catalogId, imageContent);
            CreateProductRequest request =
                    new CreateProductRequest(row.name(), row.sku(), row.description(), asset.id(), null);
            ProductResponse product = productService.create(ownerId, catalogId, request);
            return CsvImportConfirmRowResult.from(row, product.id(), List.of());
        } catch (ImageDownloadException
                | FileTooLargeException
                | UnsupportedImageFormatException
                | DuplicateSkuException
                | ProductLimitExceededException e) {
            // Motivo específico do download (timeout, DNS, SSRF) já foi logado em
            // ImageDownloader, com a mesma mensagem genérica aqui; as demais exceções reusam a
            // mensagem já existente da spec 003/004 (formato/tamanho de imagem, SKU duplicado,
            // limite de produtos) — nunca duplicada aqui.
            return CsvImportConfirmRowResult.from(row, null, List.of(e.getMessage()));
        }
    }

    private List<CsvImportRowResult> validateStructure(UUID catalogId, MultipartFile file) {
        byte[] content = readBytes(file);
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw new CsvFileTooLargeException();
        }

        List<RawCsvRow> rawRows = parse(content);
        if (rawRows.size() > MAX_ROWS) {
            throw new CsvFileTooLargeException();
        }

        Map<String, Long> skuCounts = rawRows.stream()
                .map(RawCsvRow::sku)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        // SKU duplicado dentro do próprio arquivo: todas as ocorrências viram erro, nenhuma
        // "vence" silenciosamente (spec 006, US1 cenário 6).
        Set<String> internalDuplicates =
                skuCounts.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        Set<String> existingConflicts = new HashSet<>();
        for (String sku : skuCounts.keySet()) {
            if (!internalDuplicates.contains(sku) && productRepository.existsByCatalogIdAndSku(catalogId, sku)) {
                existingConflicts.add(sku);
            }
        }

        long acceptedSoFar = productRepository.countByCatalogId(catalogId);
        List<CsvImportRowResult> results = new ArrayList<>();
        for (RawCsvRow row : rawRows) {
            List<String> errors = new ArrayList<>();
            if (row.name().isBlank()) {
                errors.add("name is required");
            }
            if (row.imageUrl().isBlank()) {
                errors.add("image is required");
            } else if (!isValidHttpUrl(row.imageUrl())) {
                errors.add("image must be a valid http(s) URL");
            }
            if (row.sku() != null && (internalDuplicates.contains(row.sku()) || existingConflicts.contains(row.sku()))) {
                errors.add("code is already in use in this catalog");
            }
            if (errors.isEmpty()) {
                if (acceptedSoFar + 1 > MAX_PRODUCTS_PER_CATALOG) {
                    errors.add("catalog has reached the maximum of 50 products");
                } else {
                    acceptedSoFar++;
                }
            }
            results.add(new CsvImportRowResult(
                    row.lineNumber(), row.name(), row.sku(), row.description(), row.imageUrl(), errors));
        }
        return results;
    }

    private void requireOwnedCatalog(UUID ownerId, UUID catalogId) {
        catalogRepository.findByIdAndOwnerId(catalogId, ownerId).orElseThrow(CatalogNotFoundException::new);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CsvReadException(e);
        }
    }

    private List<RawCsvRow> parse(byte[] content) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get();
        try (CSVParser parser = CSVParser.parse(new StringReader(new String(content, StandardCharsets.UTF_8)), format)) {
            List<RawCsvRow> rows = new ArrayList<>();
            int lineNumber = 0;
            for (CSVRecord record : parser) {
                lineNumber++;
                String name = getColumnOrEmpty(record, "nome");
                String sku = blankToNull(getColumnOrEmpty(record, "codigo"));
                String description = blankToNull(getColumnOrEmpty(record, "descricao"));
                String imageUrl = getColumnOrEmpty(record, "imagem");
                rows.add(new RawCsvRow(lineNumber, name, sku, description, imageUrl));
            }
            return rows;
        } catch (IOException e) {
            throw new CsvReadException(e);
        }
    }

    // Coluna ausente ao final de uma linha (menos campos que o cabeçalho) vira valor vazio,
    // seguindo as regras normais de validação — nunca um erro genérico de "linha malformada"
    // (spec 006, US1 cenário 8).
    private String getColumnOrEmpty(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return "";
        }
        try {
            String value = record.get(column);
            return value == null ? "" : value.trim();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private record RawCsvRow(int lineNumber, String name, String sku, String description, String imageUrl) {}
}
