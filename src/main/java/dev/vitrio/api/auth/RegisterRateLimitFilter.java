package dev.vitrio.api.auth;

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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limita tentativas de {@code POST /api/v1/auth/register}, só por IP — diferente do
 * {@link LoginRateLimitFilter}, não há bucket por e-mail aqui: um e-mail só é consumido uma vez
 * com sucesso (cadastro duplicado já é rejeitado por {@link EmailAlreadyRegisteredException}), não
 * faz sentido limitar tentativas repetidas do mesmo e-mail como faz sentido para senha errada no
 * login. Endpoint público (ADR-0002 do Vitrio) sem este limite permitiria criação de contas em
 * massa por automação. Limite (capacidade/janela) é bem mais permissivo que login, configurável
 * via {@code vitrio.rate-limit.register-*} — ver default em {@code application.yml}.
 *
 * <p>Sem cache do corpo da requisição (diferente de {@link LoginRateLimitFilter}): não precisamos
 * inspecionar o corpo para decidir o bucket, então o {@code InputStream} original segue intacto
 * para o restante da cadeia.
 */
public class RegisterRateLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher REGISTER_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/register");

    private final JsonMapper jsonMapper;
    private final int capacity;
    private final Duration refillWindow;
    private final Cache<String, Bucket> ipBuckets;

    public RegisterRateLimitFilter(JsonMapper jsonMapper, int capacity, Duration refillWindow) {
        this.jsonMapper = jsonMapper;
        this.capacity = capacity;
        this.refillWindow = refillWindow;
        this.ipBuckets = Caffeine.newBuilder().expireAfterAccess(refillWindow.multipliedBy(2)).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REGISTER_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Bucket ipBucket = ipBuckets.get(request.getRemoteAddr(), key -> newBucket());
        ConsumptionProbe probe = ipBucket.tryConsumeAndReturnRemaining(1);

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
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many registration attempts");
        problemDetail.setTitle("Too Many Requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), problemDetail);
    }
}
