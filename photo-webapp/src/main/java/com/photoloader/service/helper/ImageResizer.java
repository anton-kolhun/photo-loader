package com.photoloader.service.helper;

import static com.photoloader.service.ImageProcessorService.DEFAULT_HEIGHT;
import static com.photoloader.service.ImageProcessorService.DEFAULT_WIDTH;

import com.photoloader.service.bean.ImageResolution;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ImageResizer {


  public byte[] resizeImage(byte[] originalImage, ImageResolution imageResolution) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream is = new ByteArrayInputStream(originalImage)) {
      BufferedImage image = Thumbnails.of(is).scale(1).asBufferedImage();
      Thumbnails.of(image)
          .size(imageResolution.getWidth().orElse(DEFAULT_WIDTH),
              imageResolution.getHeight().orElse(DEFAULT_HEIGHT))
          .outputFormat("JPEG")
          .outputQuality(1)
          .toOutputStream(outputStream);
      byte[] data = outputStream.toByteArray();
      return data;
    } catch (IOException e) {
      throw new RuntimeException("error occurred while resizing the image", e);
    }
  }
}

