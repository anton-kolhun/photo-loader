package com.photoloader.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Turns a search phrase into a CLIP text embedding by invoking the text-encoder Lambda. */
@Component
@ConditionalOnClipSearch
@Slf4j
public class TextEmbeddingClient {

    private static final int CACHE_SIZE = 500;

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String functionName;
    private final Map<String, float[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public TextEmbeddingClient(LambdaClient clipLambdaClient, ObjectMapper objectMapper,
                               @Value("${clip.lambda.function-name}") String functionName) {
        this.lambdaClient = clipLambdaClient;
        this.objectMapper = objectMapper;
        this.functionName = functionName;
    }

    public Embedding embed(String text) {
        String normalized = text.strip().toLowerCase(Locale.ROOT); // CLIP's tokenizer lower-cases anyway
        float[] cached = cache.get(normalized);
        if (cached != null) {
            return new Embedding(cached, null);
        }
        long start = System.currentTimeMillis();
        JsonNode response = invoke(Map.of("text", normalized));
        if (response.hasNonNull("error")) {
            throw new IllegalArgumentException(response.get("error").asText());
        }
        JsonNode values = response.get("embedding");
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        cache.put(normalized, vector);
        long tookMs = System.currentTimeMillis() - start;
        log.debug("embedded '{}' in {} ms", normalized, tookMs);
        return new Embedding(vector, response.path("model").asText(null));
    }

    /** Fire-and-forget call so the Lambda loads its model while the user is still typing. */
    public void warmup() {
        try {
            lambdaClient.invoke(r -> r.functionName(functionName)
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromUtf8String("{\"warmup\":true}")));
        } catch (Exception e) {
            log.warn("CLIP Lambda warm-up failed: {}", e.getMessage());
        }
    }

    private JsonNode invoke(Map<String, Object> payload) {
        try {
            SdkBytes body = SdkBytes.fromByteArray(objectMapper.writeValueAsBytes(payload));
            InvokeResponse response = lambdaClient.invoke(r -> r.functionName(functionName).payload(body));
            if (response.functionError() != null) {
                throw new IllegalStateException("CLIP Lambda failed (" + response.functionError() + "): "
                        + response.payload().asUtf8String());
            }
            return objectMapper.readTree(response.payload().asByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("could not call the CLIP Lambda", e);
        }
    }

    /** {@code model} is null when served from the cache (it was already checked on the first call). */
    public record Embedding(float[] vector, String model) {
    }
}
