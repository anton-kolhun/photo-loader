package com.photoloader.controller;

import com.photoloader.service.ImageProcessorService;
import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.Quality;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("image")
@AllArgsConstructor
public class ImageController {

  private final ImageProcessorService imageProcessorService;

  @GetMapping
  public String getRandomImage(@RequestParam(value = "year", required = false) String year,
      @RequestParam(value = "height", required = false) Integer height,
      @RequestParam(value = "width", required = false) Integer width,
      @RequestParam(value = "quality", required = false) Quality quality,
      HttpSession httpSession, @RequestHeader Map<String, String> headers) {
    ImageCharacteristics imageMetaData = new ImageCharacteristics();
    if (!headers.get("user-agent").toLowerCase().contains("mobile")) {
        imageMetaData = ImageCharacteristics.builder()
                .height(height)
                .width(width)
                .quality(quality)
                .build();
    }
    byte[] image = imageProcessorService.fetchRandomImage(httpSession.getId(), year, imageMetaData);
    return Base64.getEncoder().encodeToString(image);
  }

}
