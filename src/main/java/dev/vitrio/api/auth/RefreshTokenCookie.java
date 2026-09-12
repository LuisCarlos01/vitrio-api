package dev.vitrio.api.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;

/**
 * Cookie {@code httpOnly} do Refresh token, compartilhado entre {@code login} e {@code refresh}
 * — evita duplicar nome/atributos em cada endpoint.
 */
final class RefreshTokenCookie {

    static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";

    private RefreshTokenCookie() {}

    static ResponseCookie set(String rawRefreshToken, Duration maxAge) {
        return ResponseCookie.from(NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                // None (não Strict): vitrio-web e a API vivem em domínios diferentes
                // (cross-site) — com Strict o navegador nunca reenvia o cookie pro backend.
                // Exige Secure (já setado acima). CORS com allowlist de origem (não wildcard)
                // é quem segura o CSRF que isso reabre: um POST forjado de fora da allowlist
                // ainda dispara /refresh ou /logout (rotaciona/revoga o token da vítima), mas
                // a origem maliciosa nunca consegue ler a resposta.
                .sameSite("None")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }

    // Max-Age=0 instrui o navegador a descartar o cookie imediatamente (usado em logout).
    static ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(PATH)
                .maxAge(0)
                .build();
    }

    // Ausente = null: quem chama decide o fallback (corpo do request), não esta classe.
    static String readFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
