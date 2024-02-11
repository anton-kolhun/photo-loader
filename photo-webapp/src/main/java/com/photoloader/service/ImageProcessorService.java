package com.photoloader.service;

import com.photoloader.exception.NotFoundException;
import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.ImageSessionCursor;
import com.photoloader.service.bean.Quality;
import com.photoloader.service.helper.ImageResizer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

@Service
@AllArgsConstructor
@Slf4j
public class ImageProcessorService {

    private final S3Manager s3Manager;

    private final ImageResizer imageResizer;

    private Map<String, UsedIndexInfo> sessionToUsedIndexes;

    public static final Integer DEFAULT_HEIGHT = 1500;

    public static final Integer DEFAULT_WIDTH = 900;


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
        long start = System.currentTimeMillis();
        byte[] downloadedFile = s3Manager.downloadFile(fileName);
        long end = System.currentTimeMillis();
        log.debug("image downloading time = " + (end - start) + " ms");
        ImageCharacteristics adjustedMetaData = ImageCharacteristics.builder()
                .height(metaData.getHeight().map(val -> (int) (val * 0.75)).orElse(DEFAULT_HEIGHT))
                .width(metaData.getWidth().map(val -> (int) (val * 0.75)).orElse(DEFAULT_WIDTH))
                .quality(metaData.getQuality().orElse(Quality.MEDIUM))
                .build();
        byte[] resized = imageResizer.resizeImage(downloadedFile, adjustedMetaData, fileName);
        return resized;

    }

    private int calculateAvailableIndex(ImageSessionCursor imageSessionCursor, String year, List<String> allFiles) {
        var roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        boolean isPublic = false;
        for (GrantedAuthority role : roles) {
            if (role.getAuthority().contains("ROLE_PUBLIC")) {
                isPublic = true;
                break;
            }
        }
        Random random = new Random();
        int index = random.nextInt(allFiles.size());
        UsedIndexInfo currentInfo = sessionToUsedIndexes.getOrDefault(imageSessionCursor.getSessionId(), new UsedIndexInfo());
        Predicate<String> filePredicate;
        if (isPublic) {
            filePredicate = s -> s.contains("test-user/");
        } else if (StringUtils.isEmpty(year)) {
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
