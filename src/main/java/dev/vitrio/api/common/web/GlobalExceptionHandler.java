package dev.vitrio.api.common.web;

import dev.vitrio.api.asset.AssetReadException;
import dev.vitrio.api.asset.FileTooLargeException;
import dev.vitrio.api.asset.UnsupportedImageFormatException;
import dev.vitrio.api.auth.EmailAlreadyRegisteredException;
import dev.vitrio.api.auth.InvalidCredentialsException;
import dev.vitrio.api.auth.InvalidRefreshTokenException;
import dev.vitrio.api.catalog.CatalogNotFoundException;
import dev.vitrio.api.catalog.InvalidLogoAssetException;
import dev.vitrio.api.catalog.InvalidWhatsappNumberException;
import dev.vitrio.api.catalog.WhatsappNumberNotConfiguredException;
import dev.vitrio.api.category.CategoryNotFoundException;
import dev.vitrio.api.csvimport.CsvFileTooLargeException;
import dev.vitrio.api.csvimport.CsvReadException;
import dev.vitrio.api.product.DuplicateSkuException;
import dev.vitrio.api.product.InvalidCategoryException;
import dev.vitrio.api.product.InvalidImageAssetException;
import dev.vitrio.api.product.ProductLimitExceededException;
import dev.vitrio.api.product.ProductNotFoundException;
import dev.vitrio.api.publiccatalog.PublicCatalogNotFoundException;
import dev.vitrio.api.user.UserNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tratamento de erro transversal, no formato RFC 9457 ("Problem Details for HTTP APIs"), via
 * {@link ProblemDetail} nativo do Spring — centraliza esse
 * tratamento neste pacote técnico compartilhado, exceção deliberada à organização por feature.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Email already registered");
        return problemDetail;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Invalid credentials");
        return problemDetail;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Invalid refresh token");
        return problemDetail;
    }

    @ExceptionHandler(CatalogNotFoundException.class)
    public ProblemDetail handleCatalogNotFound(CatalogNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Catalog not found");
        return problemDetail;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ProblemDetail handleCategoryNotFound(CategoryNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Category not found");
        return problemDetail;
    }

    @ExceptionHandler(CsvFileTooLargeException.class)
    public ProblemDetail handleCsvFileTooLarge(CsvFileTooLargeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("CSV file too large");
        return problemDetail;
    }

    @ExceptionHandler(CsvReadException.class)
    public ProblemDetail handleCsvReadFailure(CsvReadException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Could not read CSV file");
        return problemDetail;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Product not found");
        return problemDetail;
    }

    @ExceptionHandler(InvalidImageAssetException.class)
    public ProblemDetail handleInvalidImageAsset(InvalidImageAssetException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid image asset");
        return problemDetail;
    }

    @ExceptionHandler(InvalidLogoAssetException.class)
    public ProblemDetail handleInvalidLogoAsset(InvalidLogoAssetException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid logo asset");
        return problemDetail;
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ProblemDetail handleInvalidCategory(InvalidCategoryException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid category");
        return problemDetail;
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Duplicate SKU");
        return problemDetail;
    }

    @ExceptionHandler(ProductLimitExceededException.class)
    public ProblemDetail handleProductLimitExceeded(ProductLimitExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Product limit exceeded");
        return problemDetail;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("User not found");
        return problemDetail;
    }

    @ExceptionHandler(PublicCatalogNotFoundException.class)
    public ProblemDetail handlePublicCatalogNotFound(PublicCatalogNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Catalog not found");
        return problemDetail;
    }

    @ExceptionHandler(InvalidWhatsappNumberException.class)
    public ProblemDetail handleInvalidWhatsappNumber(InvalidWhatsappNumberException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid WhatsApp number");
        return problemDetail;
    }

    @ExceptionHandler(WhatsappNumberNotConfiguredException.class)
    public ProblemDetail handleWhatsappNumberNotConfigured(WhatsappNumberNotConfiguredException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("WhatsApp number not configured");
        return problemDetail;
    }

    @ExceptionHandler(UnsupportedImageFormatException.class)
    public ProblemDetail handleUnsupportedImageFormat(UnsupportedImageFormatException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Unsupported image format");
        return problemDetail;
    }

    @ExceptionHandler(AssetReadException.class)
    public ProblemDetail handleAssetReadFailure(AssetReadException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Could not read uploaded file");
        return problemDetail;
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ProblemDetail handleFileTooLarge(FileTooLargeException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("File too large");
        return problemDetail;
    }

    // Rede de segurança do limite do container de multipart (application.yml,
    // spring.servlet.multipart.max-file-size) — na prática FileTooLargeException já cobre o
    // limite de negócio de 10MB antes disso, mas um payload muito maior pode ser rejeitado pelo
    // Tomcat antes mesmo de chegar no service.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, new FileTooLargeException().getMessage());
        problemDetail.setTitle("File too large");
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorEntry)
                .toList();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problemDetail.setTitle("Invalid request content");
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    private Map<String, String> toErrorEntry(FieldError fieldError) {
        // Map.of lança NPE em valor nulo; getDefaultMessage() pode retornar null se a anotação de
        // validação não tiver mensagem configurada.
        String message = Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Invalid value");
        return Map.of("field", fieldError.getField(), "message", message);
    }
}
