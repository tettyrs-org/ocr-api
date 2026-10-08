package org.tettyrs.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@ApplicationScoped
public class S3ClientProducer {

    @ConfigProperty(name = "quarkus.s3.endpoint-override")
    String s3Endpoint;

    @ConfigProperty(name = "aws.access.key.id")
    String accessKeyId;

    @ConfigProperty(name = "aws.secret.access.key")
    String secretAccessKey;

    @ConfigProperty(name = "aws.region")
    String region;

    @Produces
    @ApplicationScoped
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(s3Endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }
}
