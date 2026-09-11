package dev.vitrio.api.auth;

/**
 * Corpo de {@code POST /api/v1/auth/refresh}. Sem anotação de Bean Validation:
 * exceção deliberada registrada — um valor ausente/vazio é tratado como Refresh
 * token inválido (mesmo {@code 401} genérico de {@link InvalidRefreshTokenException}), não como
 * erro de validação {@code 400}.
 */
public record RefreshRequest(String refreshToken) {}
