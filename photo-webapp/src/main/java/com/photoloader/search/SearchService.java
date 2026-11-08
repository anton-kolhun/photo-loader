package com.photoloader.search;

import com.photoloader.exception.NotFoundException;
import com.photoloader.service.S3Manager;
import com.photoloader.service.bean.ImageCharacteristics;
import com.photoloader.service.bean.Quality;
import com.photoloader.service.helper.ImageResizer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
@ConditionalOnClipSearch
@Slf4j
public class SearchService {

    static final int MAX_RESULTS = 100;
    private static final int THUMBNAIL_SIDE = 400;
    private static final Path THUMBNAIL_CACHE = Path.of(S3Manager.CACHE_FOLDER_NAME, "search-thumbnails");

    private final Path prebuiltThumbnails;

    private final EmbeddingIndex index;
    private final TextEmbeddingClient textEmbeddingClient;
    private final S3Manager s3Manager;
    private final ImageResizer imageResizer;
    // decoding a full-size photo can take ~50 MB of heap; keep a burst of thumbnail requests from exhausting it
    private final Semaphore resizePermits = new Semaphore(2);

    public SearchService(EmbeddingIndex index, TextEmbeddingClient textEmbeddingClient,
                         S3Manager s3Manager, ImageResizer imageResizer,
                         @Value("${clip.index.dir}") String indexDir) {
        this.prebuiltThumbnails = Path.of(indexDir, "thumbnails");
        this.index = index;
        this.textEmbeddingClient = textEmbeddingClient;
        this.s3Manager = s3Manager;
        this.imageResizer = imageResizer;
    }

    public List<EmbeddingIndex.Hit> search(String query, String year, int limit) {
        TextEmbeddingClient.Embedding embedding = textEmbeddingClient.embed(query);
        if (embedding.model() != null && !embedding.model().equals(index.model())) {
            throw new IllegalStateException("CLIP model mismatch: Lambda uses " + embedding.model()
                    + " but the image index was built with " + index.model());
        }
        return index.search(embedding.vector(), Math.min(Math.max(limit, 1), MAX_RESULTS), keyFilter(year));
    }

    public List<EmbeddingIndex.Hit> similar(String key, String year, int limit) {
        requireSearchable(key);
        return index.similar(key, Math.min(Math.max(limit, 1), MAX_RESULTS), keyFilter(year));
    }

    public void warmup() {
        textEmbeddingClient.warmup();
    }

    /** Screen-sized photo for the viewer; the same cached, resized file the slideshow serves. */
    public byte[] photo(String key) {
        requireSearchable(key);
        return withResizePermit(() -> s3Manager.downloadFile(key));
    }

    /**
     * Pre-built by embed_images.py and shipped with the index, so the grid never waits on S3.
     * Falls back to building one from the photo (and caching it) if it is missing.
     */
    public byte[] thumbnail(String key) {
        requireSearchable(key);
        Path prebuilt = prebuiltThumbnails.resolve(key + ".jpg");
        Path cached = THUMBNAIL_CACHE.resolve(key);
        try {
            if (Files.exists(prebuilt)) {
                return Files.readAllBytes(prebuilt);
            }
            if (Files.exists(cached)) {
                return Files.readAllBytes(cached);
            }
            byte[] thumbnail = withResizePermit(() -> imageResizer.resizeImage(
                    s3Manager.downloadFile(key), // reuses the slideshow's download cache
                    ImageCharacteristics.builder().width(THUMBNAIL_SIDE).height(THUMBNAIL_SIDE)
                            .quality(Quality.MEDIUM).build(), key));
            Files.createDirectories(cached.getParent());
            Files.write(cached, thumbnail);
            return thumbnail;
        } catch (IOException e) {
            throw new IllegalStateException("could not build thumbnail for " + key, e);
        }
    }

    private void requireSearchable(String key) {
        if (!index.contains(key) || !keyFilter(null).test(key)) {
            throw new NotFoundException("unknown photo: " + key); // only serve photos that search can return
        }
    }

    private byte[] withResizePermit(Supplier<byte[]> work) {
        try {
            resizePermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to resize a photo", e);
        }
        try {
            return work.get();
        } finally {
            resizePermits.release();
        }
    }

    /** Same visibility rules as the slideshow: test-user photos are hidden, a year narrows to that folder. */
    private static Predicate<String> keyFilter(String year) {
        Predicate<String> visible = key -> !key.contains("test-user/");
        return StringUtils.isBlank(year) ? visible : visible.and(key -> key.contains(year + "/"));
    }
}
