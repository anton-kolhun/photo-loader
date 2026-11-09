package com.photoloader.service;

import com.photoloader.exception.NotFoundException;
import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.ImageSessionCursor;
import com.photoloader.service.bean.Quality;
import com.photoloader.service.helper.ImageResizer;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageProcessorService {

    private final S3Manager s3Manager;

    private final ImageResizer imageResizer;

    private final Map<String, UsedIndexInfo> sessionToUsedIndexes = new ConcurrentHashMap<>();

    public static final Integer DEFAULT_HEIGHT = 1500;

    public static final Integer DEFAULT_WIDTH = 900;

    /** Screen-sized sizes are rounded up to this step, so the many device sizes share a few cache buckets. */
    static final int SIZE_STEP = 200;

    /** Final, screen-sized JPEGs: cache/screen/<width>x<height>-<quality>/<photo key> */
    private Path screenCacheDir = Path.of(S3Manager.CACHE_FOLDER_NAME, "screen");


    public byte[] fetchRandomImage(ImageSessionCursor cursor, String year, ImageCharacteristics metaData) {
        List<String> allFiles = s3Manager.getAllFilesInBucket();
        String fileName;
        if (cursor.getCurrentCursor() > cursor.getSeenImages().size()) {
            int index = calculateAvailableIndex(cursor, year, allFiles);
            Set<Integer> newVal = new HashSet<>(Arrays.asList(index));
            UsedIndexInfo usedIndexInfo = new UsedIndexInfo(newVal, LocalDateTime.now());
            sessionToUsedIndexes.merge(cursor.getSessionId(), usedIndexInfo, (existingInfo, newInfo) -> {
                existingInfo.getUsedIndexes().addAll(newInfo.getUsedIndexes());
                existingInfo.setLastAccessedAt(newInfo.getLastAccessedAt());
                return existingInfo;
            });
            fileName = allFiles.get(index);
            cursor.getSeenImages().add(fileName);
        } else if (cursor.getCurrentCursor() <= 0) {
            cursor.setCurrentCursor(1);
            throw new NotFoundException("There are no more previous images in the curren session");
        } else {
            fileName = cursor.getSeenImages().get(cursor.getCurrentCursor() - 1);
        }
        return forScreen(fileName, metaData);
    }

    /** A photo as the slideshow shows it on this device; also used by the search viewer so both look the same. */
    public byte[] forScreen(String fileName, ImageCharacteristics metaData) {
        return screenSized(fileName,
                metaData.getWidth().map(val -> roundUp((int) (val * 0.75))).orElse(DEFAULT_WIDTH),
                metaData.getHeight().map(val -> roundUp((int) (val * 0.75))).orElse(DEFAULT_HEIGHT),
                metaData.getQuality().orElse(Quality.MEDIUM));
    }

    /**
     * The photo resized for a screen. Built once per photo, size bucket and quality, then served from disk:
     * re-decoding and re-encoding the photo on every view cost ~140 ms of CPU on the server.
     */
    byte[] screenSized(String fileName, int width, int height, Quality quality) {
        Path cached = screenCacheDir.resolve(width + "x" + height + "-" + quality).resolve(fileName);
        try {
            if (Files.exists(cached)) {
                return Files.readAllBytes(cached);
            }
            long start = System.currentTimeMillis();
            byte[] photo = s3Manager.downloadFile(fileName); // full-quality copy, itself cached in cache/
            byte[] resized = imageResizer.resizeImage(photo, ImageCharacteristics.builder()
                    .width(width).height(height).quality(quality).build(), fileName);
            Files.createDirectories(cached.getParent());
            // write to a temp file then rename, so a concurrent request never reads a half-written image
            Path tmp = Files.createTempFile(cached.getParent(), ".resizing-", ".tmp");
            Files.write(tmp, resized);
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("image {} prepared for {}x{} {} in {} ms", fileName, width, height, quality,
                    System.currentTimeMillis() - start);
            return resized;
        } catch (IOException e) {
            throw new RuntimeException("Error preparing image " + fileName, e);
        }
    }

    static int roundUp(int size) {
        return Math.max(SIZE_STEP, (size + SIZE_STEP - 1) / SIZE_STEP * SIZE_STEP);
    }

    void setScreenCacheDir(Path screenCacheDir) { // tests
        this.screenCacheDir = screenCacheDir;
    }

    private int calculateAvailableIndex(ImageSessionCursor imageSessionCursor, String year, List<String> allFiles) {
        Random random = new Random();
        int index = random.nextInt(allFiles.size());
        UsedIndexInfo currentInfo = sessionToUsedIndexes.getOrDefault(imageSessionCursor.getSessionId(), new UsedIndexInfo());
        Predicate<String> filePredicate;
        if (StringUtils.isEmpty(year)) {
            filePredicate = s -> !s.contains("test-user/");
        } else {
            filePredicate = s -> s.contains(year + "/");
        }
        String fileName = allFiles.get(index);
        int counter = 0;
        while ((counter < 10000) && (currentInfo.getUsedIndexes().contains(index)
                || !filePredicate.test(fileName))) {
            index = random.nextInt(allFiles.size());
            fileName = allFiles.get(index);
            counter++;
        }
        if (counter == 10000) {
            imageSessionCursor.setCurrentCursor(imageSessionCursor.getCurrentCursor() - 1);
            throw new NotFoundException("no further images available for a given search criteria:"
                    + " year=" + year);
        }
        return index;
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void removeTerminatedSessions() {
        sessionToUsedIndexes.entrySet().removeIf(
                stringUsedIndexInfoEntry -> stringUsedIndexInfoEntry.getValue().getLastAccessedAt()
                        .isBefore(LocalDateTime.now().minusMinutes(10)));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class UsedIndexInfo {

        Set<Integer> usedIndexes = new HashSet<>();
        LocalDateTime lastAccessedAt;

    }

//for local testing
//  public byte[] fetchRandomImage(String sessionId, String year,  ImageResolution resolution) {
//    ClassPathResource classPathResource = new ClassPathResource("static/login_image.jpg");
//    try (InputStream is = classPathResource.getInputStream()) {
//      byte[] bytes = IOUtils.toByteArray(is);
//      return bytes;
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }
}
