package dev.vitrio.api.user;

/** Lançada quando o id do Access token não corresponde a nenhum {@code User} (spec 008) — rede de segurança, não alcançável enquanto não existir exclusão de conta. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found");
    }
}
