package dev.vitrio.api.catalog;

/** Tentativa de verificar o WhatsApp de um catálogo que ainda não tem número configurado (spec 002, US3). */
public class WhatsappNumberNotConfiguredException extends RuntimeException {

    public WhatsappNumberNotConfiguredException() {
        super("No WhatsApp number configured for this catalog");
    }
}
