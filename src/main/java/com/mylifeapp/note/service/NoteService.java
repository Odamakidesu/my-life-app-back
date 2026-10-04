package com.mylifeapp.note.service;

import com.mylifeapp.common.web.BadRequestException;
import com.mylifeapp.note.dto.NoteCreateRequest;
import com.mylifeapp.note.dto.NoteSummaryResponse;
import com.mylifeapp.note.dto.NoteUpdateRequest;
import com.mylifeapp.note.entity.Note;
import com.mylifeapp.note.entity.Recurrence;
import com.mylifeapp.note.exception.NoteNotFoundException;
import com.mylifeapp.note.query.NoteQueryRepository;
import com.mylifeapp.note.query.NoteSearchCriteria;
import com.mylifeapp.note.query.NoteSort;
import com.mylifeapp.note.repository.NoteRepository;
import com.mylifeapp.note.repository.NoteTagRepository;
import com.mylifeapp.tag.service.TagService;
import com.mylifeapp.tag.support.TagNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * メモのユースケース。
 *
 * <p>認可とトランザクション境界を差し込む唯一の場所。
 * 以前は NoteController が JdbcTemplate に直接 SQL を書いており、
 * 横断的な関心（所有者チェック・トランザクション・created_at 採番）を
 * 差し込む場所が物理的に存在しなかった。tag ドメインと同じ3層に揃える。
 *
 * <p>メモのタグは note_tags（ID の対応表）が正。返すメモの tags には、そこから組み立てた名前を詰める。
 * note.tags（名前の写し）は旧版へ戻せるように書き続けているだけで、読み出しには使わない。
 */
@Service
public class NoteService {

    /** 1ページの既定件数。既存クライアントはページ指定を送ってこないため、実用上十分な値にする。 */
    public static final int DEFAULT_PAGE_SIZE = 100;
    /** 1リクエストで取得できる上限。無制限取得を構造的に不可能にする。 */
    public static final int MAX_PAGE_SIZE = 500;
    /** エクスポートで1度に読む件数 */
    private static final int EXPORT_BATCH_SIZE = 500;

    private final NoteRepository noteRepository;
    private final NoteQueryRepository noteQueryRepository;
    private final NoteTagRepository noteTagRepository;
    private final TagService tagService;
    private final Clock clock;

    public NoteService(NoteRepository noteRepository,
                       NoteQueryRepository noteQueryRepository,
                       NoteTagRepository noteTagRepository,
                       TagService tagService,
                       Clock clock) {
        this.noteRepository = noteRepository;
        this.noteQueryRepository = noteQueryRepository;
        this.noteTagRepository = noteTagRepository;
        this.tagService = tagService;
        this.clock = clock;
    }

    /** 自分の有効なメモを条件で絞り込み、指定の順で1ページ分返す。 */
    @Transactional(readOnly = true)
    public List<Note> search(Long userId, NoteSearchCriteria criteria, int page, int size) {
        int effectiveSize = clampSize(size);
        long offset = (long) Math.max(page, 0) * effectiveSize;
        return withTags(noteQueryRepository.search(userId, criteria, now(), effectiveSize, offset));
    }

    @Transactional(readOnly = true)
    public long count(Long userId, NoteSearchCriteria criteria) {
        return noteQueryRepository.count(userId, criteria, now());
    }

    @Transactional(readOnly = true)
    public NoteSummaryResponse summarize(Long userId) {
        Map<String, Object> row = noteQueryRepository.summarize(userId, now());
        return new NoteSummaryResponse(
                ((Number) row.get("total")).longValue(),
                ((Number) row.get("overdue")).longValue(),
                ((Number) row.get("due_soon")).longValue());
    }

    /** エクスポート用に、自分の有効なメモを優先度順ですべて返す。 */
    @Transactional(readOnly = true)
    public List<Note> findAllForExport(Long userId) {
        NoteSearchCriteria criteria = new NoteSearchCriteria(
                null, List.of(), null, null, null, null, NoteSort.PRIORITY);
        LocalDateTime now = now();
        List<Note> all = new ArrayList<>();
        for (long offset = 0; ; offset += EXPORT_BATCH_SIZE) {
            List<Note> batch = noteQueryRepository.search(userId, criteria, now, EXPORT_BATCH_SIZE, offset);
            all.addAll(batch);
            if (batch.size() < EXPORT_BATCH_SIZE) {
                return withTags(all);
            }
        }
    }

    /** ゴミ箱の一覧（ゴミ箱に入れた日時の新しい順）。 */
    @Transactional(readOnly = true)
    public List<Note> findDeleted(Long userId, int page, int size) {
        int effectiveSize = clampSize(size);
        long offset = (long) Math.max(page, 0) * effectiveSize;
        return withTags(noteRepository.findDeletedByUserId(userId, effectiveSize, offset));
    }

    @Transactional(readOnly = true)
    public long countDeleted(Long userId) {
        return noteRepository.countDeletedByUserId(userId);
    }

    @Transactional
    public Note create(Long userId, NoteCreateRequest request) {
        requireDeadlineForRecurrence(request.recurrence(), request.deadline());
        List<String> tagNames = TagNames.parse(request.tags());

        Note note = new Note();
        note.setUserId(userId); // 所有者はリクエストではなく認証情報から決める
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setTags(TagNames.join(tagNames));
        note.setDeadline(request.deadline());
        note.setRecurrence(request.recurrence());
        note.setIsImportant(Boolean.TRUE.equals(request.isImportant()));
        note.setIsPinned(Boolean.TRUE.equals(request.isPinned()));
        note.setIsCompleted(false);
        note.setDeleteFlg(false);
        // created_at は NoteSaveCallback が採番する
        Note saved = noteRepository.save(note);
        noteTagRepository.replace(saved.getId(), tagService.resolveForNote(userId, tagNames));
        return withTags(saved);
    }

    @Transactional
    public Note update(Long userId, Long id, NoteUpdateRequest request) {
        requireDeadlineForRecurrence(request.recurrence(), request.deadline());
        List<String> tagNames = TagNames.parse(request.tags());
        int updated = noteRepository.updateContent(
                id, userId, request.title(), request.content(), TagNames.join(tagNames), request.deadline(),
                request.recurrence() == null ? null : request.recurrence().name());
        requireUpdated(updated, id);
        noteTagRepository.replace(id, tagService.resolveForNote(userId, tagNames));
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

    /**
     * 完了状態を変える。
     *
     * <p>繰り返しのメモを未完了から完了にしたときは、次の締切のメモを作り、繰り返しをそちらへ引き継ぐ。
     * 完了にした側の繰り返しは外すので、完了を戻してもう一度完了にしても二重には作られない。
     *
     * @return 作られた次回分のメモの ID。作られなかった場合は null
     */
    @Transactional
    public Long setCompleted(Long userId, Long id, boolean value) {
        Note current = findOwned(userId, id);
        requireUpdated(noteRepository.updateCompleted(id, userId, value), id);

        boolean becameCompleted = value && !Boolean.TRUE.equals(current.getIsCompleted());
        if (!becameCompleted || current.getRecurrence() == null || current.getDeadline() == null
                || Boolean.TRUE.equals(current.getDeleteFlg())) {
            return null;
        }

        Note next = new Note();
        next.setUserId(userId);
        next.setTitle(current.getTitle());
        next.setContent(current.getContent());
        next.setTags(current.getTags());
        next.setDeadline(current.getRecurrence().nextAfter(current.getDeadline(), now()));
        next.setRecurrence(current.getRecurrence());
        next.setIsImportant(current.getIsImportant());
        next.setIsPinned(current.getIsPinned());
        next.setIsCompleted(false);
        next.setDeleteFlg(false);
        Note saved = noteRepository.save(next);
        noteTagRepository.replace(saved.getId(), noteTagRepository.findTagIds(id));
        noteRepository.clearRecurrence(id, userId);
        return saved.getId();
    }

    @Transactional
    public void setDeleted(Long userId, Long id, boolean value) {
        requireUpdated(noteRepository.updateDeleteFlg(id, userId, value, now()), id);
    }

    /**
     * ゴミ箱にあるメモを完全に削除する。
     * ゴミ箱に無い（有効な）メモ・他人のメモ・存在しないメモは区別せず 404 にする。
     */
    @Transactional
    public void deletePermanently(Long userId, Long id) {
        requireUpdated(noteRepository.deletePermanently(id, userId), id);
    }

    @Transactional(readOnly = true)
    public Note findOwned(Long userId, Long id) {
        Note note = noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoteNotFoundException(id));
        return withTags(note);
    }

    private Note withTags(Note note) {
        withTags(List.of(note));
        return note;
    }

    /** note_tags から組み立てたタグ名をメモに詰める（返す直前の表示用。保存はしない）。 */
    private List<Note> withTags(List<Note> notes) {
        Map<Long, List<String>> names = noteTagRepository.findNamesByNoteIds(
                notes.stream().map(Note::getId).toList());
        for (Note note : notes) {
            note.setTags(TagNames.join(names.getOrDefault(note.getId(), List.of())));
        }
        return notes;
    }

    private static void requireDeadlineForRecurrence(Recurrence recurrence, LocalDateTime deadline) {
        if (recurrence != null && deadline == null) {
            throw new BadRequestException("繰り返しを設定するには締切を入力してください。");
        }
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
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
