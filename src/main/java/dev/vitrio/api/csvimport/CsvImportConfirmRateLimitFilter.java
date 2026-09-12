package dev.vitrio.api.csvimport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limita confirmações de importação de CSV ({@code POST .../products/import/confirm}) a 10 por
 * hora por {@code Reseller} (spec 006, US2 cenário 9) — bucket por revendedora autenticada, não
 * por IP, diferente de {@code LoginRateLimitFilter}/{@code RegisterRateLimitFilter} (spec 001):
 * este endpoint já exige Access token, então o IP não é o identificador relevante (várias
 * revendedoras atrás do mesmo IP/NAT não devem compartilhar limite). Por isso, ao contrário
 * daqueles dois filtros, este roda <b>depois</b> de {@code JwtAuthenticationFilter} na cadeia
 * ({@code SecurityConfig}) — precisa do {@link Authentication} já populado no
 * {@link SecurityContextHolder} pra saber de quem é o bucket.
 *
 * <p>Sem token válido, este filtro não faz nada (deixa a cadeia seguir) — quem rejeita a
 * requisição nesse caso é a checagem de autenticação padrão do Spring Security, mais adiante,
 * com o {@code 401} de sempre.
 */
public class CsvImportConfirmRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher CONFIRM_MATCHER = PathPatternRequestMatcher.pathPattern(
            HttpMethod.POST, "/api/v1/catalogs/{catalogId}/products/import/confirm");

    private final JsonMapper jsonMapper;
    private final int capacity;
    private final Duration refillWindow;
    private final Cache<String, Bucket> resellerBuckets;

    public CsvImportConfirmRateLimitFilter(JsonMapper jsonMapper, int capacity, Duration refillWindow) {
        this.jsonMapper = jsonMapper;
        this.capacity = capacity;
        this.refillWindow = refillWindow;
        this.resellerBuckets =
                Caffeine.newBuilder().expireAfterAccess(refillWindow.multipliedBy(2)).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CONFIRM_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = resellerBuckets.get(authentication.getName(), key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillWindow))
                .build();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many import confirmations");
        problemDetail.setTitle("Too Many Requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), problemDetail);
    }
}
