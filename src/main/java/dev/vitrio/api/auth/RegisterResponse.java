package dev.vitrio.api.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Corpo de resposta de sucesso de {@code POST /api/v1/auth/register}. Nunca inclui senha/hash.
 * Inclui o mesmo par de tokens de {@link LoginResponse} — registro também autentica (spec 001,
 * login automático pós-registro), evitando que a Reseller precise logar de novo logo após criar
 * a conta.
 */
public record RegisterResponse(
        UUID id,
        String email,
        Instant createdAt,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {}
