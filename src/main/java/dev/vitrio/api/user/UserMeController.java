package dev.vitrio.api.user;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autoatendimento da conta autenticada (spec 008) — controller separado de {@link UserController}
 * (listagem admin-only) porque o modelo de autorização é completamente diferente: aqui "eu" vem
 * sempre do Access token, nunca de um {@code id} de path (evita IDOR trivial por construção).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserMeController {

    private final UserService userService;

    public UserMeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserMeResponse get(Authentication authentication) {
        return userService.getMe(userId(authentication));
    }

    @PatchMapping
    public UserMeResponse update(@Valid @RequestBody UpdateUserMeRequest request, Authentication authentication) {
        return userService.updateMe(userId(authentication), request);
    }

    private UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
