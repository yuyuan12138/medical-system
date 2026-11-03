package com.medicalsystem.backend.storage.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class MinioConfig(
    @Value("\${storage.s3.endpoint-internal:http://localhost:9000}") private val internalEndpoint: String,
    @Value("\${storage.s3.endpoint-external:http://localhost:9000}") private val externalEndpoint: String,
    @Value("\${storage.s3.access-key}") private val accessKey: String,
    @Value("\${storage.s3.secret-key}") private val secretKey: String,
    @Value("\${storage.s3.region:us-east-1}") private val region: String
) {
    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .endpointOverride(URI.create(internalEndpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        return S3Presigner.builder()
            .endpointOverride(URI.create(externalEndpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
    }
}
