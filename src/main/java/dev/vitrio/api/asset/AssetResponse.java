package dev.vitrio.api.asset;

import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id, UUID catalogId, String contentType, long byteSize, String publicUrl, Instant createdAt) {

    static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getCatalogId(),
                asset.getContentType(),
                asset.getByteSize(),
                asset.getPublicUrl(),
                asset.getCreatedAt());
    }
}
