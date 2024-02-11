package com.photoloader.service.helper;

import static com.photoloader.service.ImageProcessorService.DEFAULT_HEIGHT;
import static com.photoloader.service.ImageProcessorService.DEFAULT_WIDTH;

import com.photoloader.service.bean.ImageCharacteristics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.photoloader.service.bean.Quality;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ImageResizer {


  public byte[] resizeImage(byte[] originalImage, ImageCharacteristics metaData, String fileName) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream is = new ByteArrayInputStream(originalImage)) {
      long start = System.currentTimeMillis();
      BufferedImage image = Thumbnails.of(is).scale(1).asBufferedImage();
      Thumbnails.of(image)
          .size(metaData.getWidth().orElse(DEFAULT_WIDTH),
              metaData.getHeight().orElse(DEFAULT_HEIGHT))
          .outputFormat("JPEG")
          .outputQuality(metaData.getQuality().orElse(Quality.MEDIUM).getValue())
          .toOutputStream(outputStream);
      byte[] data = outputStream.toByteArray();
      long end = System.currentTimeMillis();
      log.debug("image resizing time = " +  (end - start) + " ms");
      return data;
    } catch (Exception e) {
      throw new RuntimeException("Error occurred while resizing the image " + fileName, e);
    }
  }
}

