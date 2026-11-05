package org.tettyrs.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Memastikan enqueue menulis traceparent dari span aktif ke payload job di Redis. */
class JobQueueTracingTest {

    @BeforeAll
    static void pasangSdk() {
        // Dipasang sekali per JVM. Bila sudah terpasang oleh tes lain, pemanggilan kedua dilewati.
        try {
            OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .buildAndRegisterGlobal();
        } catch (IllegalStateException sudahTerpasang) {
            // SDK global sudah ada.
        }
    }

    @Test
    void enqueueMenulisTraceparentDariSpanAktif() {
        RedisDataSource redis = mock(RedisDataSource.class);
        @SuppressWarnings("unchecked")
        StreamCommands<String, String, String> stream = mock(StreamCommands.class);
        when(redis.stream(String.class)).thenReturn(stream);
        when(stream.xadd(eq(JobQueue.STREAM), anyMap())).thenReturn("1-0");

        JobQueue queue = new JobQueue();
        queue.redis = redis;

        Span induk = GlobalOpenTelemetry.getTracer("test").spanBuilder("induk").startSpan();
        String traceId;
        try (Scope ignored = induk.makeCurrent()) {
            traceId = induk.getSpanContext().getTraceId();
            queue.enqueue(UUID.randomUUID(), "fuel");
        } finally {
            induk.end();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(stream).xadd(eq(JobQueue.STREAM), payload.capture());
        String traceparent = payload.getValue().get("traceparent");
        assertTrue(traceparent != null && traceparent.startsWith("00-" + traceId + "-"),
                "traceparent harus memuat trace id dari span induk: " + traceparent);
        assertEquals("fuel", payload.getValue().get("category"));
    }
}
