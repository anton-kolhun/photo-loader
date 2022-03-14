package com.photoloader;

public class ImageSorterRunner {

  public static void main(String[] args) {
    FileDistributor imageDistributer = new FileDistributor();
    String bucketName = "ak-photo";
    String awsRegion = "eu-central-1";
    imageDistributer.distributeImagesByYear(bucketName, awsRegion);
  }
}
