package com.mylifeapp.note.service;

import com.mylifeapp.note.dto.NoteCreateRequest;
import com.mylifeapp.note.dto.NoteUpdateRequest;
import com.mylifeapp.note.exception.NoteNotFoundException;
import com.mylifeapp.note.model.Note;
import com.mylifeapp.note.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * メモのユースケース。
 *
 * <p>認可とトランザクション境界を差し込む唯一の場所。
 * 以前は NoteController が JdbcTemplate に直接 SQL を書いており、
 * 横断的な関心（所有者チェック・トランザクション・created_at 採番）を
 * 差し込む場所が物理的に存在しなかった。tag ドメインと同じ3層に揃える。
 */
@Service
public class NoteService {

    /** 1ページの既定件数。既存クライアントはページ指定を送ってこないため、実用上十分な値にする。 */
    public static final int DEFAULT_PAGE_SIZE = 100;
    /** 1リクエストで取得できる上限。無制限取得を構造的に不可能にする。 */
    public static final int MAX_PAGE_SIZE = 500;

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Transactional(readOnly = true)
    public List<Note> findActive(Long userId, int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long offset = (long) Math.max(page, 0) * effectiveSize;
        return noteRepository.findActiveByUserId(userId, effectiveSize, offset);
    }

    @Transactional(readOnly = true)
    public long countActive(Long userId) {
        return noteRepository.countActiveByUserId(userId);
    }

    @Transactional
    public Note create(Long userId, NoteCreateRequest request) {
        Note note = new Note();
        note.setUserId(userId); // 所有者はリクエストではなく認証情報から決める
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setTags(request.tags());
        note.setDeadline(request.deadline());
        note.setIsImportant(Boolean.TRUE.equals(request.isImportant()));
        note.setIsPinned(Boolean.TRUE.equals(request.isPinned()));
        note.setIsCompleted(false);
        note.setDeleteFlg(false);
        // created_at は NoteSaveCallback が採番する
        return noteRepository.save(note);
    }

    @Transactional
    public Note update(Long userId, Long id, NoteUpdateRequest request) {
        int updated = noteRepository.updateContent(
                id, userId, request.title(), request.content(), request.tags(), request.deadline());
        requireUpdated(updated, id);
        return findOwned(userId, id);
    }

    @Transactional
    public void setImportant(Long userId, Long id, boolean value) {
        requireUpdated(noteRepository.updateImportant(id, userId, value), id);
    }

    @Transactional
    public void setPinned(Long userId, Long id, boolean value) {
        requireUpdated(noteRepository.updatePinned(id, userId, value), id);
    }

    @Transactional
    public void setCompleted(Long userId, Long id, boolean value) {
        requireUpdated(noteRepository.updateCompleted(id, userId, value), id);
    }

    @Transactional
    public void setDeleted(Long userId, Long id, boolean value) {
        requireUpdated(noteRepository.updateDeleteFlg(id, userId, value), id);
    }

    @Transactional(readOnly = true)
    public Note findOwned(Long userId, Long id) {
        return noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoteNotFoundException(id));
    }

    /**
     * 更新件数 0 は「存在しない」か「他人のメモ」のどちらか。
     * 区別せず 404 にする。区別すると ID の総当たりで他ユーザーのメモの存在が分かってしまう。
     */
    private void requireUpdated(int updatedRows, Long id) {
        if (updatedRows == 0) {
            throw new NoteNotFoundException(id);
        }
    }
}
