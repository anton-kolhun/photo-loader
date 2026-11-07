package com.photoloader.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AWSConfig {

    @Bean
    public S3Client awsS3Client(@Value("${aws.region}") String awsRegion) {
        return buildS3Client(awsRegion);
    }

    public static S3Client buildS3Client(String awsRegion) {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // SDK v1 followed S3's wrong-region redirects automatically; v2 needs this opt-in
                .crossRegionAccessEnabled(true)
                .build();
    }
}
