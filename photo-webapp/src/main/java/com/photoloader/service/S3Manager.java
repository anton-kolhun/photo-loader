package com.photoloader.service;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class S3Manager {

    public static final String CACHE_FOLDER_NAME = "cache";
    private final AmazonS3 awsS3Client;

    public final TransferManager transferManager;

    private final String bucket;

    private List<S3ObjectSummary> allFilesCache = new ArrayList<>();

    private final Lock allFilesLock = new ReentrantLock();

    public S3Manager(AmazonS3 awsS3Client, @Value("${s3.bucketName}") String bucketName) {
        this.awsS3Client = awsS3Client;
        this.transferManager = TransferManagerBuilder.standard()
                .withS3Client(awsS3Client)
                .build();
        this.bucket = bucketName;
    }


    public List<String> getAllFilesInBucket() {
        try {
            allFilesLock.lock();
            if (allFilesCache.isEmpty()) {
                List<S3ObjectSummary> files = listAllFiles();
                allFilesCache.addAll(files);
            }
            return allFilesCache.stream()
                    .map(S3ObjectSummary::getKey)
                    .collect(Collectors.toList());
        } finally {
            allFilesLock.unlock();
        }
    }

    private List<S3ObjectSummary> listAllFiles() {
        boolean shouldContinue = true;
        ListObjectsRequest request = new ListObjectsRequest();
        request.setBucketName(bucket);
        List<S3ObjectSummary> allFiles = new ArrayList<>();
        while (shouldContinue) {
            ObjectListing listing = awsS3Client.listObjects(request);
            List<S3ObjectSummary> files = listing.getObjectSummaries();
            allFiles.addAll(files);
            if (listing.isTruncated()) {
                request.setMarker(listing.getNextMarker());
            } else {
                shouldContinue = false;
            }
        }
        return allFiles;
    }

    public byte[] downloadFile(String fileName) {
        String localFilePath = CACHE_FOLDER_NAME + "/" + fileName;
        Path pathToFIle = Path.of(localFilePath);
        if (Files.exists(pathToFIle)) {
            try {
                return Files.readAllBytes(pathToFIle);
            } catch (IOException e) {
                throw new RuntimeException("Error occurred while reading file: " + localFilePath, e);
            }
        }
        File file = new File(localFilePath);
        Download download = transferManager.download(bucket, fileName, file);
//        download.addProgressListener(
//                (ProgressListener) progressEvent -> {
//                     log.debug("Downloaded bytes: " + progressEvent.getBytesTransferred());
//                });
        try {
            download.waitForCompletion();
            byte[] res = Files.readAllBytes(Paths.get(file.getPath()));
            //file.delete();
            return res;
        } catch (AmazonClientException | InterruptedException | IOException e) {
            throw new RuntimeException("Failed to fetch data from S3: " + fileName, e);
        }
    }

    @Scheduled(cron = "0 0 0/1 * * *")
    public void updateAllFilesCache() {

        List<S3ObjectSummary> files = listAllFiles();
        try {
            allFilesLock.lock();
            allFilesCache.clear();
            allFilesCache.addAll(files);
        } finally {
            allFilesLock.unlock();
        }
    }
}

