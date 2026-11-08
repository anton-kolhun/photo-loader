package com.photoloader.service;

import com.photoloader.service.bean.Quality;
import com.photoloader.service.helper.ImageResizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageProcessorServiceTest {

    @TempDir
    Path cacheDir;

    private final S3Manager s3Manager = mock(S3Manager.class);
    private final ImageResizer imageResizer = mock(ImageResizer.class);
    private ImageProcessorService service;

    @BeforeEach
    void setUp() {
        service = new ImageProcessorService(s3Manager, imageResizer);
        service.setScreenCacheDir(cacheDir);
        when(s3Manager.downloadFile("2020/a.jpg")).thenReturn(new byte[]{1, 2, 3});
        when(imageResizer.resizeImage(any(), any(), eq("2020/a.jpg"))).thenReturn(new byte[]{9, 9});
    }

    @Test
    void resizesOnceThenServesFromDisk() throws Exception {
        assertThat(service.screenSized("2020/a.jpg", 1200, 800, Quality.MEDIUM)).containsExactly(9, 9);
        assertThat(service.screenSized("2020/a.jpg", 1200, 800, Quality.MEDIUM)).containsExactly(9, 9);

        verify(s3Manager, times(1)).downloadFile("2020/a.jpg");
        verify(imageResizer, times(1)).resizeImage(any(), any(), eq("2020/a.jpg"));
        assertThat(Files.readAllBytes(cacheDir.resolve("1200x800-MEDIUM/2020/a.jpg"))).containsExactly(9, 9);
        try (var leftovers = Files.list(cacheDir.resolve("1200x800-MEDIUM/2020"))) {
            assertThat(leftovers).hasSize(1); // no temp files left behind
        }
    }

    @Test
    void sizeAndQualityAreSeparateCacheEntries() {
        service.screenSized("2020/a.jpg", 1200, 800, Quality.MEDIUM);
        service.screenSized("2020/a.jpg", 1200, 800, Quality.HIGH);
        service.screenSized("2020/a.jpg", 600, 800, Quality.MEDIUM);

        verify(imageResizer, times(3)).resizeImage(any(), any(), eq("2020/a.jpg"));
    }

    @Test
    void roundsSizesUpToBuckets() {
        assertThat(ImageProcessorService.roundUp(1134)).isEqualTo(1200);
        assertThat(ImageProcessorService.roundUp(1200)).isEqualTo(1200);
        assertThat(ImageProcessorService.roundUp(1201)).isEqualTo(1400);
        assertThat(ImageProcessorService.roundUp(20)).isEqualTo(200);
    }
}
