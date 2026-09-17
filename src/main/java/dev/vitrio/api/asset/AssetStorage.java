package dev.vitrio.api.asset;

/**
 * Porta de armazenamento do conteúdo de um {@link Asset} — {@link S3AssetStorage} é a única
 * implementação de produção; a interface existe pra permitir um storage real equivalente
 * (LocalStack) nos testes de integração, sem mockar o {@code S3Client}.
 */
interface AssetStorage {

    /** Envia o conteúdo e devolve a URL pública de leitura do objeto gravado em {@code storageKey}. */
    String upload(String storageKey, byte[] content, String contentType);

    /** Remove o objeto gravado em {@code storageKey} (spec 012). */
    void delete(String storageKey);
}
