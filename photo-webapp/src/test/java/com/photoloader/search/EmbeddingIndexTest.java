package com.photoloader.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingIndexTest {

    @TempDir
    Path dir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ranksByCosineSimilarityAndRespectsFilterAndLimit() throws IOException {
        writeIndex(List.of("2020/beach.jpg", "2021/dog.jpg", "2021/cat.jpg", "test-user/x.jpg"), new float[][]{
                unit(1, 0, 0), unit(0.6f, 0.8f, 0), unit(0, 1, 0), unit(0, 1, 0)});
        EmbeddingIndex index = new EmbeddingIndex(dir.toString(), objectMapper);

        List<EmbeddingIndex.Hit> hits = index.search(unit(0, 1, 0), 2, key -> !key.startsWith("test-user/"));

        assertThat(hits).extracting(EmbeddingIndex.Hit::key).containsExactly("2021/cat.jpg", "2021/dog.jpg");
        assertThat(hits.get(0).score()).isCloseTo(1f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(index.search(unit(1, 0, 0), 10, key -> key.startsWith("2020/")))
                .extracting(EmbeddingIndex.Hit::key).containsExactly("2020/beach.jpg");
        assertThat(index.contains("2021/dog.jpg")).isTrue();
        assertThat(index.contains("2021/missing.jpg")).isFalse();
    }

    @Test
    void similarUsesThePhotosOwnEmbeddingAndExcludesIt() throws IOException {
        writeIndex(List.of("a.jpg", "b.jpg", "c.jpg"), new float[][]{
                unit(1, 0, 0), unit(0.9f, 0.1f, 0), unit(0, 1, 0)});
        EmbeddingIndex index = new EmbeddingIndex(dir.toString(), objectMapper);

        assertThat(index.similar("a.jpg", 2, key -> true))
                .extracting(EmbeddingIndex.Hit::key).containsExactly("b.jpg", "c.jpg");
        assertThatThrownBy(() -> index.similar("missing.jpg", 2, key -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQueryWithWrongDimension() throws IOException {
        writeIndex(List.of("a.jpg"), new float[][]{unit(1, 0, 0)});
        EmbeddingIndex index = new EmbeddingIndex(dir.toString(), objectMapper);

        assertThatThrownBy(() -> index.search(new float[]{1, 0}, 1, key -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedEmbeddingsFile() throws IOException {
        writeIndex(List.of("a.jpg", "b.jpg"), new float[][]{unit(1, 0, 0)}); // metadata says 2 rows, file has 1

        assertThatThrownBy(() -> new EmbeddingIndex(dir.toString(), objectMapper))
                .isInstanceOf(IllegalStateException.class);
    }

    private void writeIndex(List<String> keys, float[][] rows) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(rows.length * rows[0].length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float[] row : rows) {
            for (float v : row) {
                buffer.putFloat(v);
            }
        }
        Files.write(dir.resolve("embeddings.f32"), buffer.array());
        objectMapper.writeValue(dir.resolve("index.json").toFile(),
                Map.of("model", "test-model", "dim", rows[0].length, "count", keys.size(), "keys", keys));
    }

    private static float[] unit(float... v) {
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / Math.sqrt(norm));
        }
        return out;
    }
}
