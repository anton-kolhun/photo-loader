package com.photoloader.controller;

import com.photoloader.service.S3Manager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("image")
public class ImageController {

    @Autowired
    private S3Manager s3Manager;

    @GetMapping
    public String getRandomImage() {
        List<String> allFiles = s3Manager.getAllFilesInBucket();
        Random random = new Random();
        int index = random.nextInt(allFiles.size());
        String fileName = allFiles.get(index);
        byte[] downloadedFile = s3Manager.downloadFile(fileName);
        return Base64.getEncoder().encodeToString(downloadedFile);
    }

}
