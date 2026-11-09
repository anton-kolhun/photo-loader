package com.photoloader.service;

import com.photoloader.service.bean.ImageCharacteristics;
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
    void devicesMapToTheSharedCacheBuckets() throws Exception {
        String mac = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15";
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";

        service.forScreen("2020/a.jpg", ImageCharacteristics.forDevice(mac, 1512, 982, null));
        service.forScreen("2020/a.jpg", ImageCharacteristics.forDevice(iphone, 393, 852, null));

        assertThat(cacheDir.resolve("1200x800-MEDIUM/2020/a.jpg")).exists();  // MacBook: 75% of screen, rounded up
        assertThat(cacheDir.resolve("900x1500-MEDIUM/2020/a.jpg")).exists(); // every phone shares one size
    }

    @Test
    void roundsSizesUpToBuckets() {
        assertThat(ImageProcessorService.roundUp(1134)).isEqualTo(1200);
        assertThat(ImageProcessorService.roundUp(1200)).isEqualTo(1200);
        assertThat(ImageProcessorService.roundUp(1201)).isEqualTo(1400);
        assertThat(ImageProcessorService.roundUp(20)).isEqualTo(200);
    }
}
