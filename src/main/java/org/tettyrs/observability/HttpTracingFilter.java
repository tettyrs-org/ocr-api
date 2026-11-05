package org.tettyrs.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * Span server untuk setiap request HTTP. Konteks diambil dari traceparent request masuk bila ada, sehingga
 * trace dimulai dari client, lalu span job.enqueue menjadi anaknya. Hanya metadata yang dicatat: method, path,
 * dan status. Body dan header lain tidak pernah masuk ke span.
 *
 * Prioritas dibuat lebih awal daripada filter autentikasi, supaya request yang ditolak 401 tetap tercatat.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class HttpTracingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String KEY_SPAN = HttpTracingFilter.class.getName() + ".span";
    private static final String KEY_SCOPE = HttpTracingFilter.class.getName() + ".scope";

    private static final TextMapGetter<MultivaluedMap<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(MultivaluedMap<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(MultivaluedMap<String, String> carrier, String key) {
            return carrier == null ? null : carrier.getFirst(key);
        }
    };

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Context induk = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), requestContext.getHeaders(), GETTER);
        String method = requestContext.getMethod();
        Span span = GlobalOpenTelemetry.getTracer("ocr-api").spanBuilder("HTTP " + method)
                .setParent(induk)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.request.method", method)
                .setAttribute("url.path", requestContext.getUriInfo().getPath())
                .startSpan();
        requestContext.setProperty(KEY_SPAN, span);
        requestContext.setProperty(KEY_SCOPE, span.makeCurrent());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object scope = requestContext.getProperty(KEY_SCOPE);
        if (scope instanceof Scope s) {
            s.close();
        }
        Object raw = requestContext.getProperty(KEY_SPAN);
        if (raw instanceof Span span) {
            int status = responseContext.getStatus();
            span.setAttribute("http.response.status_code", status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }
    }
}
