package dev.vitrio.api.catalog;

/** Número não corresponde a um WhatsApp brasileiro válido depois de normalizado (spec 002, US2). */
public class InvalidWhatsappNumberException extends RuntimeException {

    public InvalidWhatsappNumberException() {
        super("Invalid WhatsApp number");
    }
}
