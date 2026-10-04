package com.mylifeapp.note.controller;

import com.mylifeapp.auth.userdetails.UserPrincipal;
import com.mylifeapp.note.dto.CompletedUpdateRequest;
import com.mylifeapp.note.dto.CompletedUpdateResponse;
import com.mylifeapp.note.dto.DeletedUpdateRequest;
import com.mylifeapp.note.dto.ImportantUpdateRequest;
import com.mylifeapp.note.dto.NoteCreateRequest;
import com.mylifeapp.note.dto.NoteResponse;
import com.mylifeapp.note.dto.NoteSummaryResponse;
import com.mylifeapp.note.dto.NoteUpdateRequest;
import com.mylifeapp.note.dto.PinnedUpdateRequest;
import com.mylifeapp.note.query.DueFilter;
import com.mylifeapp.note.query.NoteSearchCriteria;
import com.mylifeapp.note.query.NoteSort;
import com.mylifeapp.note.service.NoteExporter;
import com.mylifeapp.note.service.NoteService;
import com.mylifeapp.tag.support.TagNames;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final NoteExporter noteExporter;

    public NoteController(NoteService noteService, NoteExporter noteExporter) {
        this.noteService = noteService;
        this.noteExporter = noteExporter;
    }

    /**
     * 自分のメモ一覧。検索・絞り込み・並べ替えはクエリ文字列で指定する（すべて省略可）。
     *
     * <ul>
     *   <li>q: タイトルか本文に含まれる文字列</li>
     *   <li>tags: カンマ区切りのタグ名。すべてを含むメモに絞る</li>
     *   <li>pinned / important: true ならピン留め・スターだけ</li>
     *   <li>completed: true なら完了済みだけ、false なら未完了だけ</li>
     *   <li>due: OVERDUE（期限切れ）/ SOON（24時間以内）</li>
     *   <li>sort: PRIORITY / DEADLINE / CREATED（省略時は CREATED。以前の並び）</li>
     * </ul>
     *
     * <p>レスポンスは従来どおり JSON 配列のまま返し、条件に合う総件数は X-Total-Count ヘッダに載せる。
     * オブジェクトで包むと既存のフロントエンドが壊れるため。
     */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> getAllNotes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + NoteService.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) Boolean important,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) DueFilter due,
            @RequestParam(required = false) NoteSort sort) {

        Long userId = principal.getUserId();
        NoteSearchCriteria criteria = new NoteSearchCriteria(
                q, TagNames.parse(tags), pinned, important, completed, due, sort);
        List<NoteResponse> body = noteService.search(userId, criteria, page, size).stream()
                .map(NoteResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(noteService.count(userId, criteria)))
                .body(body);
    }

    /** 一覧の上部に出す集計（全件・期限切れ・24時間以内）。 */
    @GetMapping("/summary")
    public NoteSummaryResponse getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return noteService.summarize(principal.getUserId());
    }

    /**
     * 自分のメモ（ゴミ箱以外）をファイルとして書き出す。format は json（既定）か csv。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportNotes(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam(defaultValue = "json") String format) {
        List<NoteResponse> notes = noteService.findAllForExport(principal.getUserId()).stream()
                .map(NoteResponse::from)
                .toList();
        return noteExporter.export(notes, format);
    }

    /**
     * ゴミ箱（論理削除済みのメモ）の一覧。形は {@link #getAllNotes} と同じ。
     * 復元は既存の PUT /{id}/deleted に delete_flg=false を送る。
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<NoteResponse>> getDeletedNotes(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + NoteService.DEFAULT_PAGE_SIZE) int size) {

        Long userId = principal.getUserId();
        List<NoteResponse> body = noteService.findDeleted(userId, page, size).stream()
                .map(NoteResponse::from)
                .toList();

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(noteService.countDeleted(userId)))
                .body(body);
    }

    @PostMapping
    public ResponseEntity<NoteResponse> createNote(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody NoteCreateRequest request) {
        NoteResponse created = NoteResponse.from(noteService.create(principal.getUserId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public NoteResponse updateNote(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody NoteUpdateRequest request) {
        return NoteResponse.from(noteService.update(principal.getUserId(), id, request));
    }

    @PutMapping("/{id}/important")
    public ResponseEntity<Void> updateImportant(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ImportantUpdateRequest request) {
        noteService.setImportant(principal.getUserId(), id, request.important());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/pinned")
    public ResponseEntity<Void> updatePinned(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody PinnedUpdateRequest request) {
        noteService.setPinned(principal.getUserId(), id, request.pinned());
        return ResponseEntity.ok().build();
    }

    /**
     * メモを完了済にする。繰り返しのメモを完了にすると次回分が作られ、その ID を返す。
     * 以前は本文の無い 200 だった。本文を読まないクライアントには影響しない。
     */
    @PutMapping("/{id}/completed")
    public CompletedUpdateResponse updateCompleted(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody CompletedUpdateRequest request) {
        return new CompletedUpdateResponse(
                noteService.setCompleted(principal.getUserId(), id, request.completed()));
    }

    /** メモを論理削除する */
    @PutMapping("/{id}/deleted")
    public ResponseEntity<Void> updateNoteDelete(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody DeletedUpdateRequest request) {
        noteService.setDeleted(principal.getUserId(), id, request.deleteFlg());
        return ResponseEntity.ok().build();
    }

    /** ゴミ箱にあるメモを完全に削除する（ゴミ箱に無いメモは 404） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotePermanently(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long id) {
        noteService.deletePermanently(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
