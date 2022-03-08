package com.photoloader.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class S3Manager {

    private AmazonS3 awsS3Client;

    private TransferManager transferManager;

    private String bucket;

    public S3Manager(AmazonS3 awsS3Client, @Value("${s3.bucketName}") String bucketName) {
        this.awsS3Client = awsS3Client;
        this.transferManager = TransferManagerBuilder.standard()
                .withS3Client(awsS3Client)
                .build();
        this.bucket = bucketName;
    }


    public List<String> getAllFilesInBucket() {
        List<S3ObjectSummary> files = awsS3Client.listObjects(bucket).getObjectSummaries();
        return files.stream()
                .map(S3ObjectSummary::getKey)
                .collect(Collectors.toList());
    }

    public byte[] downloadFile(String fileName) {
        File file = new File(String.valueOf(System.currentTimeMillis()));
        Download download = transferManager.download(bucket, fileName, file);
        download.addProgressListener(
                (ProgressListener) progressEvent -> System.out.println("Downloaded bytes: " + progressEvent.getBytesTransferred()));
        try {
            download.waitForCompletion();
            byte[] res = Files.readAllBytes(Paths.get(file.getPath()));
            file.delete();
            return res;
        } catch (AmazonClientException | InterruptedException | IOException e) {
            throw new RuntimeException("Failed to fetch data from S3: " + fileName, e);
        }
    }
}

