package com.photoloader.service;

import com.photoloader.service.helper.ImageResizer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ImageProcessorService {

  private final S3Manager s3Manager;

  private final ImageResizer imageResizer;

  private Map<String, UsedIndexInfo> sessionToUsedIndexes;


  public byte[] fetchRandomImage(String sessionId, String year) {
    List<String> allFiles = s3Manager.getAllFilesInBucket();
    Random random = new Random();
    int index = random.nextInt(allFiles.size());
    UsedIndexInfo currentInfo = sessionToUsedIndexes.getOrDefault(sessionId, new UsedIndexInfo());
    Predicate<String> filePredicate;
    if (year == null) {
      filePredicate = s -> true;
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
      index = currentInfo.getUsedIndexes().iterator().next();
    }
    fileName = allFiles.get(index);
    byte[] downloadedFile = s3Manager.downloadFile(fileName);
    byte[] resized = imageResizer.resizeImage(downloadedFile);
    Set<Integer> newVal = new HashSet<>(Arrays.asList(index));
    UsedIndexInfo usedIndexInfo = new UsedIndexInfo(newVal, LocalDateTime.now());
    sessionToUsedIndexes.merge(sessionId, usedIndexInfo, (existingInfo, newInfo) -> {
      existingInfo.getUsedIndexes().addAll(newInfo.getUsedIndexes());
      existingInfo.setLastAccessedAt(newInfo.getLastAccessedAt());
      return existingInfo;
    });
    return resized;
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

//  public byte[] fetchRandomImage(String sessionId, String year) {
//    ClassPathResource classPathResource = new ClassPathResource("static/login_image.jpg");
//    try (InputStream is = classPathResource.getInputStream()) {
//      byte[] bytes = IOUtils.toByteArray(is);
//      return bytes;
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//  }
}
