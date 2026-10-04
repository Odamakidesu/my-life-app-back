package com.mylifeapp.note.dto;

/**
 * 一覧の上部に出す集計。完了済みのメモは期限切れ・期限間近に数えない。
 *
 * @param total   ゴミ箱以外のメモの数
 * @param overdue 締切を過ぎた未完了のメモの数
 * @param dueSoon 24時間以内に締切が来る未完了のメモの数
 */
public record NoteSummaryResponse(long total, long overdue, long dueSoon) {
}
