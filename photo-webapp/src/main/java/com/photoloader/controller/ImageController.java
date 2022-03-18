package com.photoloader.controller;

import com.photoloader.service.ImageProcessorService;
import java.util.Base64;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("image")
@AllArgsConstructor
public class ImageController {

  private final ImageProcessorService imageProcessorService;

  @GetMapping
  public String getRandomImage(@RequestParam(value = "year", required = false) String year,
      HttpSession httpSession) {
    byte[] image = imageProcessorService.fetchRandomImage(httpSession.getId(), year);
    return Base64.getEncoder().encodeToString(image);
  }

}
