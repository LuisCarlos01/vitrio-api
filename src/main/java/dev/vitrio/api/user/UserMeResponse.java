package dev.vitrio.api.user;

import java.util.UUID;

/**
 * Corpo de {@code GET}/{@code PATCH /api/v1/users/me} (spec 008). Tipo próprio — não reaproveita
 * {@link UserSummaryResponse} (shape parecido, mas semanticamente diferente: "meus dados" vs.
 * item de uma listagem admin-only, mesmo raciocínio já documentado em {@code UserSummaryResponse}).
 */
public record UserMeResponse(UUID id, String email, String name) {

    static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getEmail(), user.getName());
    }
}
