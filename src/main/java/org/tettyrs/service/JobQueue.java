package org.tettyrs.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Antrian job pemrosesan dokumen di Redis Streams (api-spec bagian 8, D-08).
 * Pengiriman at-least-once: job bisa sampai dua kali, sehingga konsumen (ms-ocr) wajib idempoten.
 */
@ApplicationScoped
public class JobQueue {

    public static final String STREAM = "ocr:jobs";

    private static final Logger LOG = Logger.getLogger(JobQueue.class);

    @Inject
    RedisDataSource redis;

    /**
     * Penyisipan traceparent ke payload. Konteks trace tidak ikut lewat Redis secara otomatis, jadi dimasukkan
     * sebagai field eksplisit. Konsumen (ms-ocr) memulihkannya sebelum memulai span-nya.
     */
    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

    /** Menambahkan job untuk satu dokumen. Mengembalikan id entri stream. */
    public String enqueue(UUID documentId, String category) {
        StreamCommands<String, String, String> stream = redis.stream(String.class);
        Map<String, String> payload = new HashMap<>();
        payload.put("document_id", documentId.toString());
        payload.put("category", category == null ? "other" : category);
        payload.put("enqueued_at", Instant.now().toString());
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), payload, SETTER);
        String id = stream.xadd(STREAM, payload);
        LOG.infof("job dokumen %s masuk antrian (%s)", documentId, id);
        return id;
    }
}
