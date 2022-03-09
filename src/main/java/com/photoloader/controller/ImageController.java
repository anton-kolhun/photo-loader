package com.photoloader.controller;

import com.photoloader.service.ImageProcessorService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("image")
@AllArgsConstructor
public class ImageController {

    private final ImageProcessorService imageProcessorService;

    @GetMapping
    public String getRandomImage() {
        byte[] image = imageProcessorService.fetchRandomImage();
        return Base64.getEncoder().encodeToString(image);
    }

}