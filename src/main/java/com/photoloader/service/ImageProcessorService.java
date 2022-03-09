package com.photoloader.service;

import com.photoloader.service.helper.ImageResizer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Random;

@Service
@AllArgsConstructor
public class ImageProcessorService {

    private final S3Manager s3Manager;

    private final ImageResizer imageResizer;


    @GetMapping
    public byte[] fetchRandomImage() {
        List<String> allFiles = s3Manager.getAllFilesInBucket();
        Random random = new Random();
        int index = random.nextInt(allFiles.size());
        String fileName = allFiles.get(index);
        byte[] downloadedFile = s3Manager.downloadFile(fileName);
        return imageResizer.resizeImage(downloadedFile);
    }
}
