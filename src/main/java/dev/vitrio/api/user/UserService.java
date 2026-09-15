package dev.vitrio.api.user;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra operações sobre {@link User}: listagem admin-only (`GET /api/v1/users`) e o
 * autoatendimento `GET`/`PATCH /api/v1/users/me` (spec 008) — sem paginação/filtro na listagem
 * (YAGNI frente à escala documentada do projeto).
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserSummaryResponse> listAll() {
        return userRepository.findAll().stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getEmail(), user.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(UUID userId) {
        return UserMeResponse.from(findByIdOrThrow(userId));
    }

    @Transactional
    public UserMeResponse updateMe(UUID userId, UpdateUserMeRequest request) {
        User user = findByIdOrThrow(userId);
        user.updateName(request.name());
        return UserMeResponse.from(user);
    }

    // "eu" vem sempre do Access token (Authentication), nunca de um parâmetro do cliente — não é
    // um caminho alcançável por um usuário deletado enquanto o token ainda é válido, já que não
    // existe exclusão de conta no projeto; rede de segurança, não cenário testável hoje.
    private User findByIdOrThrow(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
