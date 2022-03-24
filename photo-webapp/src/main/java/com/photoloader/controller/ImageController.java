package com.photoloader.controller;

import com.photoloader.service.ImageProcessorService;
import com.photoloader.service.bean.ImageResolution;
import java.util.Base64;
import java.util.Map;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
      @RequestParam(value = "height", required = false) Integer height,
      @RequestParam(value = "width", required = false) Integer width,
      HttpSession httpSession, @RequestHeader Map<String, String> headers) {
    ImageResolution resolution = new ImageResolution();
    if (!headers.get("user-agent").toLowerCase().contains("mobile")) {
      resolution = ImageResolution.builder()
          .height(height)
          .width(width)
          .build();
    }
    byte[] image = imageProcessorService.fetchRandomImage(httpSession.getId(), year, resolution);
    return Base64.getEncoder().encodeToString(image);
  }

}
