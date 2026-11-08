package com.photoloader.search;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;

import java.net.URI;

/**
 * Text-to-image search. Every bean in this package is marked {@link ConditionalOnClipSearch}, so with
 * clip.search.enabled unset or false none of it is created and the application behaves exactly as before.
 */
@Configuration
@ConditionalOnClipSearch
public class SearchConfig {

    @Bean
    public LambdaClient clipLambdaClient(@Value("${clip.lambda.region:${aws.region}}") String region,
                                         @Value("${clip.lambda.endpoint:}") String endpoint) {
        LambdaClientBuilder builder = LambdaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (StringUtils.isNotBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint)); // local testing: clip-search/local_lambda.py
        }
        return builder.build();
    }
}
