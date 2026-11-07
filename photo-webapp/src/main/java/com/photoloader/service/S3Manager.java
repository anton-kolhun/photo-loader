package com.photoloader.service;

import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.Quality;
import com.photoloader.service.helper.ImageResizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

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
    private final S3Client awsS3Client;

    private final String bucket;

    private List<S3Object> allFilesCache = new ArrayList<>();

    private final Lock allFilesLock = new ReentrantLock();

    private final ImageResizer imageResizer;

    public S3Manager(S3Client awsS3Client, @Value("${s3.bucketName}") String bucketName, ImageResizer imageResizer) {
        this.awsS3Client = awsS3Client;
        this.bucket = bucketName;
        this.imageResizer = imageResizer;
    }


    public List<String> getAllFilesInBucket() {
        try {
            allFilesLock.lock();
            if (allFilesCache.isEmpty()) {
                List<S3Object> files = listAllFiles();
                allFilesCache.addAll(files);
            }
            return allFilesCache.stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } finally {
            allFilesLock.unlock();
        }
    }

    public List<S3Object> listAllFiles() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .build();
        // the paginator follows continuation tokens until the whole bucket is listed
        return awsS3Client.listObjectsV2Paginator(request)
                .contents()
                .stream()
                .collect(Collectors.toList());
    }

    public byte[] downloadFile(String fileName, boolean resize) {
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
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();
        try {
            byte[] downloadedFile = awsS3Client.getObjectAsBytes(request).asByteArray();
            ImageCharacteristics adjustedMetaData = ImageCharacteristics.builder()
                    .quality(Quality.HIGH)
                    .build();
            if (resize) {
                downloadedFile = imageResizer.resizeImage(downloadedFile, adjustedMetaData, fileName);
            }
            Files.write(Paths.get(file.getPath()), downloadedFile);
            //file.delete();
            return downloadedFile;
        } catch (SdkException | IOException e) {
            throw new RuntimeException("Failed to fetch data from S3: " + fileName, e);
        }
    }

    public byte[] downloadFile(String fileName) {
        return downloadFile(fileName, true);
    }

    @Scheduled(cron = "0 0 0/1 * * *")
    public void updateAllFilesCache() {

        List<S3Object> files = listAllFiles();
        try {
            allFilesLock.lock();
            allFilesCache.clear();
            allFilesCache.addAll(files);
        } finally {
            allFilesLock.unlock();
        }
    }
}

