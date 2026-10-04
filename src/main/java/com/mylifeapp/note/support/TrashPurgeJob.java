package com.mylifeapp.note.support;

import com.mylifeapp.note.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * ゴミ箱に入れてから保持期間（既定 30 日）を過ぎたメモを完全に削除する。
 *
 * <p>タスクが複数動いていても、同じ条件の DELETE が重なるだけで結果は変わらない（冪等）。
 * 保持期間は app.trash.retention-days、実行時刻は app.trash.purge-cron で変えられる。
 */
@Component
public class TrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    private final NoteRepository noteRepository;
    private final Clock clock;
    private final int retentionDays;

    public TrashPurgeJob(NoteRepository noteRepository,
                         Clock clock,
                         @Value("${app.trash.retention-days:30}") int retentionDays) {
        this.noteRepository = noteRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.trash.purge-cron:0 30 3 * * *}", zone = "${app.time-zone:Asia/Tokyo}")
    @Transactional
    public int purge() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusDays(retentionDays);
        int purged = noteRepository.purgeDeletedBefore(threshold);
        log.info("trash_purged count={} threshold={}", purged, threshold);
        return purged;
    }

    public int retentionDays() {
        return retentionDays;
    }
}
