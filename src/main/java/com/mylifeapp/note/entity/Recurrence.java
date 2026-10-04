package com.mylifeapp.note.entity;

import java.time.LocalDateTime;

/** 繰り返しタスクの間隔。DB には名前（DAILY など）で保存する。 */
public enum Recurrence {
    DAILY,
    WEEKLY,
    MONTHLY;

    /** 1回分先の日時。MONTHLY は月末を越えない（1/31 の次は 2/28 か 2/29）。 */
    public LocalDateTime advance(LocalDateTime from) {
        return switch (this) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }

    /**
     * 次回の締切。前回の締切から間隔ずつ進め、基準時刻より後になった最初の日時を返す。
     * 期限を大きく過ぎてから完了にしても、次回分がいきなり期限切れで作られないようにする。
     */
    public LocalDateTime nextAfter(LocalDateTime previousDeadline, LocalDateTime now) {
        LocalDateTime next = advance(previousDeadline);
        while (!next.isAfter(now)) {
            next = advance(next);
        }
        return next;
    }
}
