package com.photoloader;

import com.amazonaws.services.s3.AmazonS3;
import com.photoloader.service.S3Manager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "security.authorization=basic",
        "aws.region=eu-central-1",
        "s3.bucketName=test-bucket"
})
class AppRunnerTest {

    @MockBean
    private AmazonS3 amazonS3;

    @MockBean
    private S3Manager s3Manager;

    @Test
    void contextLoads() {
    }
}
