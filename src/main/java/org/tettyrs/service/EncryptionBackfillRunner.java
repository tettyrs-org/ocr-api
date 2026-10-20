package org.tettyrs.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Backfill enkripsi sekali saat startup, dikendalikan variabel lingkungan TETTYRS_BACKFILL_ENCRYPTION:
 * {@code off} (default), {@code dry} (hanya menghitung), atau {@code apply} (mengenkripsi).
 * Setelah dijalankan dengan {@code apply}, kembalikan ke {@code off}. Hanya jumlah yang dicatat, tanpa isi data.
 */
@ApplicationScoped
public class EncryptionBackfillRunner {

    private static final Logger LOG = Logger.getLogger(EncryptionBackfillRunner.class);

    @ConfigProperty(name = "tettyrs.backfill.encryption", defaultValue = "off")
    String mode;

    @Inject
    EncryptionBackfillService backfill;

    void onStart(@Observes StartupEvent ev) {
        String m = mode == null ? "off" : mode.trim().toLowerCase();
        if (m.equals("off")) return;
        if (!m.equals("dry") && !m.equals("apply")) {
            LOG.warnf("TETTYRS_BACKFILL_ENCRYPTION tidak dikenal, backfill dilewati");
            return;
        }
        boolean apply = m.equals("apply");
        EncryptionBackfillService.Report r = backfill.run(apply, "system:backfill");
        LOG.infof("backfill enkripsi (apply=%s): dokumen=%d teks=%d berkas=%d hasil_ocr=%d",
                apply, r.documents(), r.textFields(), r.files(), r.processingResults());
    }
}
