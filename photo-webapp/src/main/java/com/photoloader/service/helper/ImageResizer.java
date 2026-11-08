package com.photoloader.service.helper;

import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.Quality;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

import static com.photoloader.service.ImageProcessorService.DEFAULT_HEIGHT;
import static com.photoloader.service.ImageProcessorService.DEFAULT_WIDTH;

@Component
@Slf4j
public class ImageResizer {

  static {
    // On a small heap, let Thumbnailator decode large photos at reduced resolution (source subsampling)
    // instead of as a full-size bitmap: a 24 MP photo is ~100 MB as a bitmap, the heap is 150 MB.
    // The output is screen-sized, so the subsampled source is still larger than what is produced.
    if (System.getProperty("thumbnailator.conserveMemoryWorkaround") == null) {
      System.setProperty("thumbnailator.conserveMemoryWorkaround", "true");
    }
  }

  // Each resize holds a decoded bitmap; a burst of uncached photos must not exhaust the heap.
  private static final Semaphore RESIZE_PERMITS = new Semaphore(2);

  public byte[] resizeImage(byte[] originalImage, ImageCharacteristics metaData, String fileName) {
    try {
      RESIZE_PERMITS.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting to resize the image " + fileName, e);
    }
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream is = new ByteArrayInputStream(originalImage)) {
      long start = System.currentTimeMillis();
      // decode and shrink in one pass (the photo used to be decoded to a full-size copy first)
      Thumbnails.of(is)
          .size(metaData.getWidth().orElse(DEFAULT_WIDTH),
              metaData.getHeight().orElse(DEFAULT_HEIGHT))
          .outputFormat("JPEG")
          .outputQuality(metaData.getQuality().orElse(Quality.MEDIUM).getValue())
          .toOutputStream(outputStream);
      byte[] data = outputStream.toByteArray();
      log.debug("image {} resizing time = {} ms", fileName, System.currentTimeMillis() - start);
      return data;
    } catch (Exception e) {
      throw new RuntimeException("Error occurred while resizing the image " + fileName, e);
    } finally {
      RESIZE_PERMITS.release();
    }
  }
}
