package dev.vitrio.api.config;

import dev.vitrio.api.auth.JwtAuthenticationFilter;
import dev.vitrio.api.auth.JwtService;
import dev.vitrio.api.auth.LoginRateLimitFilter;
import dev.vitrio.api.auth.RegisterRateLimitFilter;
import dev.vitrio.api.auth.RestAccessDeniedHandler;
import dev.vitrio.api.auth.RestAuthenticationEntryPoint;
import dev.vitrio.api.csvimport.CsvImportConfirmRateLimitFilter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /api/v1/auth/register}, {@code /login} e {@code /refresh} continuam públicos, assim como
 * {@code /api/v1/public/**} (vitrine pública por slug, spec 005 — sem Access token, consumida
 * pelo {@code Customer} anônimo), o health check do Actuator ({@code /actuator/health},
 * {@code /actuator/info} — únicos expostos, ver {@code application.yml}) e o Swagger UI/OpenAPI:
 * sem essas exceções, todos caíam no {@code anyRequest().authenticated()} e exigiam Access token.
 * {@code /logout} (e qualquer rota futura) exige um Access token válido, verificado pelo
 * {@link JwtAuthenticationFilter}. {@code
 * /api/v1/users} exige, além de autenticação, o papel
 * {@code ADMIN} — enforcement por rota (`hasRole`), não `@PreAuthorize`/method security, já que é
 * a única rota restrita por papel no projeto até agora. Falhas de autenticação e de autorização
 * são traduzidas para RFC 9457 por {@link RestAuthenticationEntryPoint} e
 * {@link RestAccessDeniedHandler}, respectivamente. {@code /login} passa pelo
 * {@link LoginRateLimitFilter} e {@code /register} pelo
 * {@link RegisterRateLimitFilter} (Vitrio spec 001), ambos antes de qualquer verificação de
 * credenciais/persistência. {@code POST .../products/import/confirm} passa pelo
 * {@link CsvImportConfirmRateLimitFilter} (spec 006), depois do {@link JwtAuthenticationFilter} —
 * diferente dos dois anteriores, o bucket é por {@code Reseller} autenticada, não por IP.
 *
 * <p>CSRF desabilitado: a API é stateless via JWT, sem sessão nem cookie de sessão do servidor
 * (ver docs/architecture.md) — a proteção CSRF do Spring Security existe para autenticação
 * baseada em sessão/cookie, que este projeto não usa.
 *
 * <p>CORS habilitado com {@code allowCredentials=true} porque o Refresh token viaja num cookie
 * {@code httpOnly} (ver {@link dev.vitrio.api.auth.RefreshTokenCookie}), não só no corpo —
 * sem isso o navegador descarta o cookie de resposta em requisições cross-origin. Por isso as
 * origens permitidas ({@code vitrio.cors.allowed-origins}) não podem incluir wildcard.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            JsonMapper jsonMapper,
            CorsConfigurationSource corsConfigurationSource,
            @Value("${vitrio.rate-limit.login-capacity}") int loginRateLimitCapacity,
            @Value("${vitrio.rate-limit.login-window-seconds}") long loginRateLimitWindowSeconds,
            @Value("${vitrio.rate-limit.register-capacity}") int registerRateLimitCapacity,
            @Value("${vitrio.rate-limit.register-window-seconds}") long registerRateLimitWindowSeconds,
            @Value("${vitrio.rate-limit.csv-import-confirm-capacity}") int csvImportConfirmRateLimitCapacity,
            @Value("${vitrio.rate-limit.csv-import-confirm-window-seconds}") long csvImportConfirmRateLimitWindowSeconds)
            throws Exception {
        LoginRateLimitFilter loginRateLimitFilter = new LoginRateLimitFilter(
                jsonMapper, loginRateLimitCapacity, Duration.ofSeconds(loginRateLimitWindowSeconds));
        RegisterRateLimitFilter registerRateLimitFilter = new RegisterRateLimitFilter(
                jsonMapper, registerRateLimitCapacity, Duration.ofSeconds(registerRateLimitWindowSeconds));
        CsvImportConfirmRateLimitFilter csvImportConfirmRateLimitFilter = new CsvImportConfirmRateLimitFilter(
                jsonMapper,
                csvImportConfirmRateLimitCapacity,
                Duration.ofSeconds(csvImportConfirmRateLimitWindowSeconds));

        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers("/api/v1/public/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/users")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(jsonMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(jsonMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(registerRateLimitFilter, JwtAuthenticationFilter.class)
                // Ao contrário dos dois acima, roda DEPOIS do JwtAuthenticationFilter: precisa do
                // Authentication já populado pra saber de qual Reseller é o bucket (ver Javadoc de
                // CsvImportConfirmRateLimitFilter).
                .addFilterAfter(csvImportConfirmRateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${vitrio.cors.allowed-origins:}") String rawAllowedOrigins) {
        // Sem property.split-values do Spring pra evitar um elemento "" fantasma na lista quando
        // a property está vazia (o que bloquearia sub-repticiamente todas as origens de verdade).
        List<String> allowedOrigins = Arrays.stream(rawAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        // Falha já na subida se allowed-origins vier com "*" — combinado com allowCredentials=true
        // isso é inválido e, sem essa checagem, só quebraria na primeira requisição de verdade.
        configuration.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Escopo restrito a /api/v1/**: os únicos endpoints consumidos pelo frontend browser
        // (ver docs/architecture.md) — Actuator e Swagger não precisam de CORS.
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }
}
