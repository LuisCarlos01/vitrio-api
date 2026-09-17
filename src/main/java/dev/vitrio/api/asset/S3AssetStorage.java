package dev.vitrio.api.asset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
class S3AssetStorage implements AssetStorage {

    private final S3Client s3Client;
    private final String bucketName;

    S3AssetStorage(S3Client s3Client, @Value("${vitrio.aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public String upload(String storageKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        // Leitura pública é responsabilidade da bucket policy (spec 003), não de ACL por
        // objeto — o path gerado pelo sistema (não-adivinhável) é a proteção real.
        return s3Client.utilities().getUrl(builder -> builder.bucket(bucketName).key(storageKey)).toString();
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(storageKey).build());
    }
}
