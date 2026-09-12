package dev.vitrio.api.csvimport;

import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.CatalogRepository;
import dev.vitrio.api.product.ProductRepository;
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

    public CsvImportService(CatalogRepository catalogRepository, ProductRepository productRepository) {
        this.catalogRepository = catalogRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CsvImportRowResult> validate(UUID ownerId, UUID catalogId, MultipartFile file) {
        requireOwnedCatalog(ownerId, catalogId);

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
