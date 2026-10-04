package com.mylifeapp.note.dto;

/**
 * 完了状態の更新結果。
 *
 * @param nextNoteId 繰り返しのメモを完了にしたときに作られた次回分の ID。作られなかった場合は null
 */
public record CompletedUpdateResponse(Long nextNoteId) {
}
