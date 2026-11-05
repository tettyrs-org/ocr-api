package org.tettyrs.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.tettyrs.security.CurrentUser;
import org.tettyrs.service.RateLimitService;
import org.tettyrs.service.RateLimitService.RateLimitResult;

import java.net.URI;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Undangan publik dibatasi per IP dan total. Permintaan yang melewati batas harus dihentikan. */
class RateLimitingInvitationTest {

    private static RateLimitResult izin(boolean allowed) {
        return new RateLimitResult(allowed, 0, 0, allowed ? Optional.empty() : Optional.of("600"));
    }

    private static ContainerRequestContext permintaan(String path, String cfIp) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uri = mock(UriInfo.class);
        when(uri.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uri);
        when(ctx.getMethod()).thenReturn("POST");
        when(ctx.getHeaderString("CF-Connecting-IP")).thenReturn(cfIp);
        return ctx;
    }

    private static RateLimitingFilter filter(RateLimitService service) {
        RateLimitingFilter f = new RateLimitingFilter();
        f.rateLimitService = service;
        f.currentUser = mock(CurrentUser.class);
        return f;
    }

    @Test
    void permintaanMelewatiBatasPerIpDihentikan() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.checkLimit(eq("ip:1.2.3.4"), anyString(), anyInt(), eq(true))).thenReturn(izin(false));
        ContainerRequestContext ctx = permintaan("/api/v1/invitations/check", "1.2.3.4");

        filter(service).filter(ctx);

        verify(ctx).abortWith(org.mockito.ArgumentMatchers.any());
        verify(service, never()).checkLimit(eq("ip:semua"), anyString(), anyInt(), eq(true));
    }

    @Test
    void batasTotalBerlakuWalauIpBerbeda() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.checkLimit(anyString(), anyString(), anyInt(), eq(true))).thenAnswer(inv ->
                "ip:semua".equals(inv.getArgument(0)) ? izin(false) : izin(true));
        ContainerRequestContext ctx = permintaan("/api/v1/invitations/redeem", "9.9.9.9");

        filter(service).filter(ctx);

        verify(ctx).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void permintaanDalamBatasDiteruskan() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        when(service.checkLimit(anyString(), anyString(), anyInt(), eq(true))).thenReturn(izin(true));
        ContainerRequestContext ctx = permintaan("/api/v1/invitations/check", "5.6.7.8");

        filter(service).filter(ctx);

        verify(ctx, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }
}
