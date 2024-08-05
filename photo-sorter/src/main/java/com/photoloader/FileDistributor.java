package com.photoloader;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.photoloader.service.S3Manager;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.photoloader.service.helper.ImageResizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileDistributor {

  public void distributeImagesByYear(String bucketName, String awsRegion) {
    AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
        .withCredentials(new DefaultAWSCredentialsProviderChain())
        .withRegion(awsRegion)
        .build();
    ImageResizer imageResizer = new ImageResizer();
    S3Manager s3Manager = new S3Manager(amazonS3, bucketName, imageResizer);
    List<S3ObjectSummary> files = new ArrayList<>();
    boolean shouldContinue = true;
    ListObjectsRequest request = new ListObjectsRequest();
    request.setBucketName(bucketName);
    while (shouldContinue) {
      ObjectListing listing = amazonS3.listObjects(request);
      List<S3ObjectSummary> filesOnPage = listing.getObjectSummaries();
      files.addAll(filesOnPage);
      if (listing.isTruncated()) {
          request.setMarker(listing.getNextMarker());
      } else {
          shouldContinue = false;
      }
    }
    //TODO: initialize thread pool to process batch in parallel;
    for (S3ObjectSummary s3ObjectSummary : files) {
      try {
        String file = s3ObjectSummary.getKey();
        if (file.contains("/")) {
          continue;
        }
        byte[] fileBinary = s3Manager.downloadFile(file);
        Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(fileBinary));
        ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        LocalDateTime localDateTime = date.toInstant()
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime();
        amazonS3.copyObject(bucketName, file, bucketName, localDateTime.getYear() + "/" + file);
        log.info("copied file = {} to {} ", file, localDateTime.getYear());
        amazonS3.deleteObject(bucketName, file);
      } catch (Exception e) {
        log.info("error occurred while copying {}", s3ObjectSummary.getKey(), e);
      }
    }
    s3Manager.transferManager.shutdownNow();
    log.info("files have been successfully restructured");
  }

}
