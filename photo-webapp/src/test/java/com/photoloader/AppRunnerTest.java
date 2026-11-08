package com.photoloader;

import com.photoloader.search.SearchController;
import com.photoloader.search.TextEmbeddingClient;
import com.photoloader.service.S3Manager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "security.authorization=basic",
        "aws.region=eu-central-1",
        "s3.bucketName=test-bucket"
})
class AppRunnerTest {

    @MockBean
    private S3Client s3Client;

    @MockBean
    private S3Manager s3Manager;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void searchFeatureIsOffByDefault() {
        assertThat(context.getBeanNamesForType(SearchController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TextEmbeddingClient.class)).isEmpty();
    }
}
