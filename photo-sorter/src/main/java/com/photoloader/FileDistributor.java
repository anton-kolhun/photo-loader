package com.photoloader;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.photoloader.config.AWSConfig;
import com.photoloader.service.S3Manager;
import com.photoloader.service.helper.ImageResizer;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
public class FileDistributor {

  public void distributeImagesByYear(String bucketName, String awsRegion) {
    try (S3Client s3Client = AWSConfig.buildS3Client(awsRegion)) {
      S3Manager s3Manager = new S3Manager(s3Client, bucketName, new ImageResizer());
      List<S3Object> files = s3Manager.listAllFiles();
      //TODO: initialize thread pool to process batch in parallel;
      for (S3Object s3Object : files) {
        String file = s3Object.key();
        try {
          if (file.contains("/")) {
            continue;
          }
          byte[] fileBinary = s3Manager.downloadFile(file, false);
          Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(fileBinary));
          ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
          Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
          LocalDateTime localDateTime = date.toInstant()
              .atZone(ZoneOffset.UTC)
              .toLocalDateTime();
          s3Client.copyObject(req -> req
              .sourceBucket(bucketName).sourceKey(file)
              .destinationBucket(bucketName).destinationKey(localDateTime.getYear() + "/" + file));
          log.info("copied file = {} to {} ", file, localDateTime.getYear());
          s3Client.deleteObject(req -> req.bucket(bucketName).key(file));
        } catch (Exception e) {
          log.info("error occurred while copying {}", file, e);
        }
      }
    }
    log.info("files have been successfully restructured");
  }

}
