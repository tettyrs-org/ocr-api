package org.tettyrs.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validasi URL webhook terhadap SSRF: hanya HTTPS publik. */
class CallbackUrlTest {

    @Test
    void httpsPublikDiterima() {
        assertTrue(CallbackService.urlWebhookAman("https://hooks.example.com/callback"));
        assertTrue(CallbackService.urlWebhookAman("https://203.0.113.10/callback"));
    }

    @Test
    void httpTanpaTlsDitolak() {
        assertFalse(CallbackService.urlWebhookAman("http://hooks.example.com/callback"));
    }

    @Test
    void localhostDanAlamatPrivatDitolak() {
        assertFalse(CallbackService.urlWebhookAman("https://localhost/x"));
        assertFalse(CallbackService.urlWebhookAman("https://127.0.0.1/x"));
        assertFalse(CallbackService.urlWebhookAman("https://10.1.2.3/x"));
        assertFalse(CallbackService.urlWebhookAman("https://192.168.1.5/x"));
        assertFalse(CallbackService.urlWebhookAman("https://172.16.0.9/x"));
        assertFalse(CallbackService.urlWebhookAman("https://169.254.169.254/latest/meta-data"));
        assertFalse(CallbackService.urlWebhookAman("https://[::1]/x"));
        assertFalse(CallbackService.urlWebhookAman("https://ms-ocr.internal/x"));
    }

    @Test
    void bentukLainAlamatLoopbackDitolak() {
        assertFalse(CallbackService.urlWebhookAman("https://localhost./x"));
        assertFalse(CallbackService.urlWebhookAman("https://2130706433/x"));
        assertFalse(CallbackService.urlWebhookAman("https://0x7f000001/x"));
        assertFalse(CallbackService.urlWebhookAman("https://127.1/x"));
    }

    @Test
    void kredensialDiUrlDitolak() {
        assertFalse(CallbackService.urlWebhookAman("https://user:pass@hooks.example.com/x"));
    }

    @Test
    void urlRusakAtauKosongDitolak() {
        assertFalse(CallbackService.urlWebhookAman(null));
        assertFalse(CallbackService.urlWebhookAman(""));
        assertFalse(CallbackService.urlWebhookAman("bukan url"));
    }
}
