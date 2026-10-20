package org.tettyrs.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
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

    /** Menambahkan job untuk satu dokumen. Mengembalikan id entri stream. */
    public String enqueue(UUID documentId, String category) {
        StreamCommands<String, String, String> stream = redis.stream(String.class);
        String id = stream.xadd(STREAM, Map.of(
                "document_id", documentId.toString(),
                "category", category == null ? "other" : category,
                "enqueued_at", Instant.now().toString()));
        LOG.infof("job dokumen %s masuk antrian (%s)", documentId, id);
        return id;
    }
}
