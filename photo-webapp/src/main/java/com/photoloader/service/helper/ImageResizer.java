package com.photoloader.service.helper;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageResizer {

    public byte[] resizeImage(byte[] originalImage) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             InputStream is = new ByteArrayInputStream(originalImage)) {
            BufferedImage image = Thumbnails.of(is).scale(1).asBufferedImage();
            Thumbnails.of(image)
                    .size(900, 1200)
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

