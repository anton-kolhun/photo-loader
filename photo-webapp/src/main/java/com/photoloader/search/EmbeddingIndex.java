package com.photoloader.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Image embeddings produced offline by clip-search/embed_images.py, held in memory.
 * ~2k photos x 512 floats is ~4 MB, so search is an exact scan: no ANN index needed.
 */
@Component
@ConditionalOnClipSearch
@Slf4j
public class EmbeddingIndex {

    private final String model;
    private final int dim;
    private final List<String> keys;
    private final Set<String> keySet;
    private final float[] vectors; // row-major, keys.size() x dim, rows L2-normalised

    public EmbeddingIndex(@Value("${clip.index.dir}") String indexDir, ObjectMapper objectMapper) throws IOException {
        Path dir = Path.of(indexDir);
        IndexMetadata meta = objectMapper.readValue(dir.resolve("index.json").toFile(), IndexMetadata.class);
        byte[] raw = Files.readAllBytes(dir.resolve("embeddings.f32"));
        if (raw.length != (long) meta.count() * meta.dim() * Float.BYTES || meta.keys().size() != meta.count()) {
            throw new IllegalStateException("CLIP index in " + dir + " is inconsistent: "
                    + meta.count() + " x " + meta.dim() + " expected, got " + raw.length + " bytes");
        }
        this.vectors = new float[meta.count() * meta.dim()];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vectors);
        this.model = meta.model();
        this.dim = meta.dim();
        this.keys = List.copyOf(meta.keys());
        this.keySet = Set.copyOf(meta.keys());
        log.info("loaded CLIP index: {} photos, dim {}, model {}", keys.size(), dim, model);
    }

    public String model() {
        return model;
    }

    public boolean contains(String key) {
        return keySet.contains(key);
    }

    /** Top {@code limit} photos by cosine similarity (dot product of unit vectors) among keys passing the filter. */
    public List<Hit> search(float[] query, int limit, Predicate<String> keyFilter) {
        if (query.length != dim) {
            throw new IllegalArgumentException("query has " + query.length + " dims, index has " + dim);
        }
        PriorityQueue<Hit> top = new PriorityQueue<>(Comparator.comparingDouble(Hit::score)); // min-heap
        for (int row = 0; row < keys.size(); row++) {
            if (!keyFilter.test(keys.get(row))) {
                continue;
            }
            float score = 0;
            for (int i = 0, offset = row * dim; i < dim; i++) {
                score += vectors[offset + i] * query[i];
            }
            if (top.size() < limit) {
                top.add(new Hit(keys.get(row), score));
            } else if (score > top.peek().score()) {
                top.poll();
                top.add(new Hit(keys.get(row), score));
            }
        }
        List<Hit> result = new ArrayList<>(top);
        result.sort(Comparator.comparingDouble(Hit::score).reversed());
        return result;
    }

    public record Hit(String key, float score) {
    }

    record IndexMetadata(String model, int dim, int count, List<String> keys) {
    }
}
