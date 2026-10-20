package org.tettyrs.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/** Job berkala penghapusan berkas (D-11). Tiap replika aman menjalankannya karena penghapusan per dokumen idempoten. */
@ApplicationScoped
public class FilePurgeJob {

    private static final Logger LOG = Logger.getLogger(FilePurgeJob.class);

    @Inject
    FilePurgeService filePurgeService;

    @Scheduled(every = "1h", identity = "file-purge", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void run() {
        int purged = filePurgeService.purgePending(LocalDateTime.now());
        if (purged > 0) {
            LOG.infof("job penghapusan berkas: %d berkas dihapus", purged);
        }
    }
}
