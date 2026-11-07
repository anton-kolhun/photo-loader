package com.photoloader;

import com.photoloader.service.S3Manager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

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

    @Test
    void contextLoads() {
    }
}
