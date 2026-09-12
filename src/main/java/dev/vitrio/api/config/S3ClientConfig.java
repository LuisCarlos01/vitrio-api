package dev.vitrio.api.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Cliente S3 (spec 003) apontado pro bucket configurado em {@code vitrio.aws.s3.*} (variáveis
 * {@code AWS_S3_*}, ver {@code .env.example}). {@code endpoint-override} é opcional e usado só
 * nos testes de integração (LocalStack) — em produção nunca é configurado, e o cliente aponta
 * pro endpoint real da AWS.
 */
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(
            @Value("${vitrio.aws.s3.region}") String region,
            @Value("${vitrio.aws.s3.access-key-id}") String accessKeyId,
            @Value("${vitrio.aws.s3.secret-access-key}") String secretAccessKey,
            @Value("${vitrio.aws.s3.endpoint-override:}") String endpointOverride) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        if (!endpointOverride.isBlank()) {
            // LocalStack exige path-style (bucket.endpoint/key não resolve pro container de teste).
            builder.endpointOverride(URI.create(endpointOverride)).forcePathStyle(true);
        }
        return builder.build();
    }
}
