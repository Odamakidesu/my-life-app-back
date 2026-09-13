package com.mylifeapp.note.support;

import com.mylifeapp.note.model.Note;
import org.springframework.data.relational.core.conversion.MutableAggregateChange;
import org.springframework.data.relational.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * created_at をサーバー側で採番する。
 *
 * <p>note.created_at は NOT NULL だが、これまでクライアントが送ってくる前提になっており、
 * 送られてこないと DataIntegrityViolationException になっていた。
 * tags 側は既に TagSaveCallback で同じことをしており、note だけがこの流儀から外れていた。
 */
@Component
public class NoteSaveCallback implements BeforeSaveCallback<Note> {

    private final Clock clock;

    public NoteSaveCallback(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Note onBeforeSave(Note note, MutableAggregateChange<Note> aggregateChange) {
        if (note.getCreatedAt() == null) {
            // 秒に切り捨てる。note.created_at は DATETIME（秒精度）なので、
            // ナノ秒付きのまま保存すると MySQL 側で丸められ、
            // 「作成レスポンスが返した時刻」と「保存された時刻」が最大1秒ずれる。
            // 画面上は作成直後とリロード後で表示時刻が食い違うことになる。
            note.setCreatedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        }
        return note;
    }
}
